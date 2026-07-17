// Native Vulkan renderer for the trip flyover: a self-contained neon wireframe
// of one trip, drawn as an additive GL_LINES list (track + elevation curtain +
// ground grid, built on the Kotlin side) with a slow orbiting camera and a
// draw-on reveal driven by a per-vertex distance fraction. Depth test is off so
// every line adds into the glow; there is no map, no texture, no lighting — the
// look is pure wireframe.
//
// Every step is failure-tolerant: any VkResult error aborts init and the Kotlin
// side falls back to the 2D Compose renderer. The app is the HOME launcher, so
// the native layer must never crash it.

#define VK_USE_PLATFORM_ANDROID_KHR
#include <vulkan/vulkan.h>

#include <android/log.h>
#include <android/native_window.h>

#include <atomic>
#include <chrono>
#include <cmath>
#include <cstring>
#include <mutex>
#include <thread>
#include <vector>

#include "flyover_renderer.h"
#include "line_frag_spv.h"
#include "line_vert_spv.h"

#define LOG_TAG "TripFlyover"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define VK_CHECK(expr)                                        \
    do {                                                      \
        VkResult _res = (expr);                               \
        if (_res != VK_SUCCESS) {                             \
            LOGE("%s failed: %d", #expr, _res);               \
            return false;                                     \
        }                                                     \
    } while (0)

namespace {

constexpr int kFloatsPerVertex = 7;
constexpr int kMaxFramesInFlight = 2;
// Camera constants are mirrored by the 2D fallback (TripFlyoverFallback.kt:
// ORBIT_RATE / ELEVATION_RAD) so the two renderers read the same; edit both.
constexpr float kOrbitRate = 0.16f;          // rad/s
constexpr float kElevationRad = 0.58f;       // ~33 deg look-down
constexpr float kPi = 3.14159265358979323846f;

// ---- Minimal column-major mat4 math (GLSL-compatible) ----------------------

struct Mat4 {
    float m[16];
};

struct Vec3 {
    float x, y, z;
};

Vec3 sub(const Vec3 &a, const Vec3 &b) { return {a.x - b.x, a.y - b.y, a.z - b.z}; }
Vec3 cross(const Vec3 &a, const Vec3 &b) {
    return {a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x};
}
float dot(const Vec3 &a, const Vec3 &b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
Vec3 normalize(const Vec3 &a) {
    float len = std::sqrt(dot(a, a));
    if (len < 1e-6f) return {0, 0, 0};
    return {a.x / len, a.y / len, a.z / len};
}

// Vulkan clip space: Y points down and z is [0, 1]; the -f on row 1 and the
// z remap bake both in so no viewport Y-flip is needed.
Mat4 perspective(float fovyRad, float aspect, float zNear, float zFar) {
    float f = 1.0f / std::tan(fovyRad * 0.5f);
    Mat4 r{};
    r.m[0] = f / aspect;
    r.m[5] = -f;
    r.m[10] = zFar / (zNear - zFar);
    r.m[11] = -1.0f;
    r.m[14] = (zNear * zFar) / (zNear - zFar);
    return r;
}

Mat4 lookAt(const Vec3 &eye, const Vec3 &center, const Vec3 &up) {
    Vec3 fwd = normalize(sub(center, eye));
    Vec3 side = normalize(cross(fwd, up));
    Vec3 u = cross(side, fwd);
    Mat4 r{};
    r.m[0] = side.x;  r.m[4] = side.y;  r.m[8] = side.z;   r.m[12] = -dot(side, eye);
    r.m[1] = u.x;     r.m[5] = u.y;     r.m[9] = u.z;      r.m[13] = -dot(u, eye);
    r.m[2] = -fwd.x;  r.m[6] = -fwd.y;  r.m[10] = -fwd.z;  r.m[14] = dot(fwd, eye);
    r.m[15] = 1.0f;
    return r;
}

Mat4 multiply(const Mat4 &a, const Mat4 &b) {
    Mat4 r{};
    for (int c = 0; c < 4; ++c) {
        for (int row = 0; row < 4; ++row) {
            float sum = 0.0f;
            for (int k = 0; k < 4; ++k) sum += a.m[k * 4 + row] * b.m[c * 4 + k];
            r.m[c * 4 + row] = sum;
        }
    }
    return r;
}

struct PushConstants {
    float mvp[16];
    float progress;
    float time;
    float aspect;
    float pad;
    float head[4];  // rgb = comet-head mix target (theme-dependent); [3] unused
};

// One frame's theme, snapshotted from the atomics in a tight burst at the top of
// the frame so the clear colour, comet head, and blend pipeline are all drawn
// from the same instant — a theme push mid-frame can't tear one into another.
struct ThemeSnapshot {
    float clear[3];
    float head[3];
    bool dark;
};

}  // namespace

struct FlyoverRenderer {
    // Instance-lifetime objects (survive stop/start).
    VkInstance instance = VK_NULL_HANDLE;

    // Surface-lifetime objects (rebuilt each start).
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkPhysicalDevice physical = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    uint32_t queueFamily = 0;
    VkQueue queue = VK_NULL_HANDLE;

    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkFormat format = VK_FORMAT_B8G8R8A8_UNORM;
    VkExtent2D extent{};
    std::vector<VkImage> images;
    std::vector<VkImageView> views;
    std::vector<VkFramebuffer> framebuffers;

    VkRenderPass renderPass = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
    // Two pipelines sharing one layout + shaders, differing only in the blend
    // dst factor: additive glow for the dark scene, alpha-over for the light one.
    // The render thread binds one per frame from the current theme (themeDark).
    VkPipeline pipeline = VK_NULL_HANDLE;      // additive (dark scene)
    VkPipeline pipelineOver = VK_NULL_HANDLE;  // alpha-over (light scene)
    VkCommandPool commandPool = VK_NULL_HANDLE;
    std::vector<VkCommandBuffer> commandBuffers;

    std::vector<VkSemaphore> imageAvailable;
    std::vector<VkSemaphore> renderFinished;
    std::vector<VkFence> inFlight;
    std::vector<VkFence> imagesInFlight;
    size_t currentFrame = 0;

    VkBuffer vertexBuffer = VK_NULL_HANDLE;
    VkDeviceMemory vertexMemory = VK_NULL_HANDLE;
    uint32_t vertexCount = 0;

    ANativeWindow *window = nullptr;

    // Cross-thread state.
    std::thread renderThread;
    std::atomic<bool> running{false};
    std::atomic<bool> needsRecreate{false};
    std::atomic<float> progress{0.0f};
    std::atomic<int> pendingWidth{0};
    std::atomic<int> pendingHeight{0};

    // Theme, pushed from Kotlin (flyover_set_theme) and read on the render thread.
    // Kotlin applies the theme before the render thread starts (setTheme runs once
    // the instance exists), so these dark defaults are a never-rendered safety
    // fallback. They mirror the dark palette (TripSceneBackground 0xFF050810 =
    // 5/8/16 over 255, white comet head) so any stray pre-push frame still reads.
    std::atomic<bool> themeDark{true};
    std::atomic<float> clearR{0x05 / 255.0f};
    std::atomic<float> clearG{0x08 / 255.0f};
    std::atomic<float> clearB{0x10 / 255.0f};
    std::atomic<float> headR{1.0f};
    std::atomic<float> headG{1.0f};
    std::atomic<float> headB{1.0f};

    std::mutex trackMutex;
    std::vector<float> pendingTrack;
    bool trackDirty = false;

    // ---- helpers -----------------------------------------------------------

    uint32_t findMemoryType(uint32_t typeBits, VkMemoryPropertyFlags props) {
        VkPhysicalDeviceMemoryProperties memProps;
        vkGetPhysicalDeviceMemoryProperties(physical, &memProps);
        for (uint32_t i = 0; i < memProps.memoryTypeCount; ++i) {
            if ((typeBits & (1u << i)) &&
                (memProps.memoryTypes[i].propertyFlags & props) == props) {
                return i;
            }
        }
        return UINT32_MAX;
    }

    bool createInstance() {
        VkApplicationInfo app{};
        app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        app.pApplicationName = "femto-trip-flyover";
        app.apiVersion = VK_API_VERSION_1_1;

        const char *exts[] = {"VK_KHR_surface", "VK_KHR_android_surface"};
        VkInstanceCreateInfo ci{};
        ci.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
        ci.pApplicationInfo = &app;
        ci.enabledExtensionCount = 2;
        ci.ppEnabledExtensionNames = exts;
        VK_CHECK(vkCreateInstance(&ci, nullptr, &instance));
        return true;
    }

    bool createSurface() {
        VkAndroidSurfaceCreateInfoKHR ci{};
        ci.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
        ci.window = window;
        VK_CHECK(vkCreateAndroidSurfaceKHR(instance, &ci, nullptr, &surface));
        return true;
    }

    bool pickDevice() {
        uint32_t count = 0;
        vkEnumeratePhysicalDevices(instance, &count, nullptr);
        if (count == 0) {
            LOGE("no Vulkan physical devices");
            return false;
        }
        std::vector<VkPhysicalDevice> devices(count);
        vkEnumeratePhysicalDevices(instance, &count, devices.data());
        for (auto dev : devices) {
            uint32_t qCount = 0;
            vkGetPhysicalDeviceQueueFamilyProperties(dev, &qCount, nullptr);
            std::vector<VkQueueFamilyProperties> families(qCount);
            vkGetPhysicalDeviceQueueFamilyProperties(dev, &qCount, families.data());
            for (uint32_t i = 0; i < qCount; ++i) {
                VkBool32 present = VK_FALSE;
                vkGetPhysicalDeviceSurfaceSupportKHR(dev, i, surface, &present);
                if ((families[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) && present) {
                    physical = dev;
                    queueFamily = i;
                    return true;
                }
            }
        }
        LOGE("no graphics+present queue family");
        return false;
    }

    bool createDevice() {
        float priority = 1.0f;
        VkDeviceQueueCreateInfo q{};
        q.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
        q.queueFamilyIndex = queueFamily;
        q.queueCount = 1;
        q.pQueuePriorities = &priority;

        const char *exts[] = {"VK_KHR_swapchain"};
        VkDeviceCreateInfo ci{};
        ci.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
        ci.queueCreateInfoCount = 1;
        ci.pQueueCreateInfos = &q;
        ci.enabledExtensionCount = 1;
        ci.ppEnabledExtensionNames = exts;
        VK_CHECK(vkCreateDevice(physical, &ci, nullptr, &device));
        vkGetDeviceQueue(device, queueFamily, 0, &queue);
        return true;
    }

    bool createSwapchain(int width, int height) {
        VkSurfaceCapabilitiesKHR caps;
        VK_CHECK(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physical, surface, &caps));

        uint32_t formatCount = 0;
        VK_CHECK(vkGetPhysicalDeviceSurfaceFormatsKHR(physical, surface, &formatCount, nullptr));
        if (formatCount == 0) {
            LOGE("no surface formats");
            return false;
        }
        std::vector<VkSurfaceFormatKHR> formats(formatCount);
        VK_CHECK(vkGetPhysicalDeviceSurfaceFormatsKHR(physical, surface, &formatCount, formats.data()));
        VkSurfaceFormatKHR chosen = formats[0];
        for (const auto &f : formats) {
            if (f.format == VK_FORMAT_B8G8R8A8_UNORM &&
                f.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                chosen = f;
                break;
            }
        }
        format = chosen.format;

        extent = caps.currentExtent;
        if (extent.width == UINT32_MAX) {
            extent.width = std::max(caps.minImageExtent.width,
                                    std::min(caps.maxImageExtent.width, (uint32_t) width));
            extent.height = std::max(caps.minImageExtent.height,
                                     std::min(caps.maxImageExtent.height, (uint32_t) height));
        }
        if (extent.width == 0 || extent.height == 0) return false;

        uint32_t imageCount = caps.minImageCount + 1;
        if (caps.maxImageCount > 0 && imageCount > caps.maxImageCount) {
            imageCount = caps.maxImageCount;
        }

        VkSwapchainCreateInfoKHR ci{};
        ci.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
        ci.surface = surface;
        ci.minImageCount = imageCount;
        ci.imageFormat = chosen.format;
        ci.imageColorSpace = chosen.colorSpace;
        ci.imageExtent = extent;
        ci.imageArrayLayers = 1;
        ci.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
        ci.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
        ci.preTransform = caps.currentTransform;
        ci.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        ci.presentMode = VK_PRESENT_MODE_FIFO_KHR;  // guaranteed, vsync-paced
        ci.clipped = VK_TRUE;
        VK_CHECK(vkCreateSwapchainKHR(device, &ci, nullptr, &swapchain));

        vkGetSwapchainImagesKHR(device, swapchain, &imageCount, nullptr);
        images.resize(imageCount);
        vkGetSwapchainImagesKHR(device, swapchain, &imageCount, images.data());

        views.resize(imageCount);
        for (uint32_t i = 0; i < imageCount; ++i) {
            VkImageViewCreateInfo vi{};
            vi.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
            vi.image = images[i];
            vi.viewType = VK_IMAGE_VIEW_TYPE_2D;
            vi.format = format;
            vi.components = {VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY,
                             VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY};
            vi.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
            VK_CHECK(vkCreateImageView(device, &vi, nullptr, &views[i]));
        }
        return true;
    }

    bool createRenderPass() {
        VkAttachmentDescription color{};
        color.format = format;
        color.samples = VK_SAMPLE_COUNT_1_BIT;
        color.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
        color.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        color.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        color.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        color.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        color.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

        VkAttachmentReference ref{0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
        VkSubpassDescription sub{};
        sub.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
        sub.colorAttachmentCount = 1;
        sub.pColorAttachments = &ref;

        VkSubpassDependency dep{};
        dep.srcSubpass = VK_SUBPASS_EXTERNAL;
        dep.dstSubpass = 0;
        dep.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dep.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dep.srcAccessMask = 0;
        dep.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

        VkRenderPassCreateInfo ci{};
        ci.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
        ci.attachmentCount = 1;
        ci.pAttachments = &color;
        ci.subpassCount = 1;
        ci.pSubpasses = &sub;
        ci.dependencyCount = 1;
        ci.pDependencies = &dep;
        VK_CHECK(vkCreateRenderPass(device, &ci, nullptr, &renderPass));
        return true;
    }

    bool createShaderModule(const uint32_t *code, size_t size, VkShaderModule *out) {
        VkShaderModuleCreateInfo ci{};
        ci.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
        ci.codeSize = size;
        ci.pCode = code;
        VK_CHECK(vkCreateShaderModule(device, &ci, nullptr, out));
        return true;
    }

    bool createPipeline() {
        VkShaderModule vert = VK_NULL_HANDLE, frag = VK_NULL_HANDLE;
        if (!createShaderModule(kLineVertSpv, kLineVertSpv_size, &vert)) return false;
        if (!createShaderModule(kLineFragSpv, kLineFragSpv_size, &frag)) return false;

        VkPipelineShaderStageCreateInfo stages[2]{};
        stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
        stages[0].module = vert;
        stages[0].pName = "main";
        stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
        stages[1].module = frag;
        stages[1].pName = "main";

        VkVertexInputBindingDescription binding{};
        binding.binding = 0;
        binding.stride = kFloatsPerVertex * sizeof(float);
        binding.inputRate = VK_VERTEX_INPUT_RATE_VERTEX;

        VkVertexInputAttributeDescription attrs[3]{};
        attrs[0] = {0, 0, VK_FORMAT_R32G32B32_SFLOAT, 0};
        attrs[1] = {1, 0, VK_FORMAT_R32G32B32_SFLOAT, 3 * sizeof(float)};
        attrs[2] = {2, 0, VK_FORMAT_R32_SFLOAT, 6 * sizeof(float)};

        VkPipelineVertexInputStateCreateInfo vin{};
        vin.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
        vin.vertexBindingDescriptionCount = 1;
        vin.pVertexBindingDescriptions = &binding;
        vin.vertexAttributeDescriptionCount = 3;
        vin.pVertexAttributeDescriptions = attrs;

        VkPipelineInputAssemblyStateCreateInfo ia{};
        ia.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
        ia.topology = VK_PRIMITIVE_TOPOLOGY_LINE_LIST;

        VkPipelineViewportStateCreateInfo vp{};
        vp.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
        vp.viewportCount = 1;
        vp.scissorCount = 1;

        VkPipelineRasterizationStateCreateInfo rs{};
        rs.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
        rs.polygonMode = VK_POLYGON_MODE_FILL;
        rs.cullMode = VK_CULL_MODE_NONE;
        rs.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
        rs.lineWidth = 1.0f;

        VkPipelineMultisampleStateCreateInfo ms{};
        ms.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
        ms.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

        // The fragment shader outputs premultiplied-alpha colour (rgb = col*cov,
        // a = cov). Both pipelines take src=ONE; they differ only in the dst
        // factor: dark = ONE (additive glow, lines accumulate onto the black
        // scene), light = ONE_MINUS_SRC_ALPHA (over, lines composite onto the
        // light scene). One scene drawn, two blend models.
        VkColorComponentFlags writeMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                                          VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
        VkPipelineColorBlendAttachmentState additive{};
        additive.blendEnable = VK_TRUE;
        additive.srcColorBlendFactor = VK_BLEND_FACTOR_ONE;
        additive.dstColorBlendFactor = VK_BLEND_FACTOR_ONE;
        additive.colorBlendOp = VK_BLEND_OP_ADD;
        additive.srcAlphaBlendFactor = VK_BLEND_FACTOR_ONE;
        additive.dstAlphaBlendFactor = VK_BLEND_FACTOR_ONE;
        additive.alphaBlendOp = VK_BLEND_OP_ADD;
        additive.colorWriteMask = writeMask;

        VkPipelineColorBlendAttachmentState over = additive;
        over.dstColorBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
        over.dstAlphaBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;

        VkPipelineColorBlendStateCreateInfo cbAdditive{};
        cbAdditive.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
        cbAdditive.attachmentCount = 1;
        cbAdditive.pAttachments = &additive;
        VkPipelineColorBlendStateCreateInfo cbOver = cbAdditive;
        cbOver.pAttachments = &over;

        VkDynamicState dyn[2] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
        VkPipelineDynamicStateCreateInfo ds{};
        ds.sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO;
        ds.dynamicStateCount = 2;
        ds.pDynamicStates = dyn;

        VkPushConstantRange push{};
        push.stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
        push.offset = 0;
        push.size = sizeof(PushConstants);

        VkPipelineLayoutCreateInfo pl{};
        pl.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
        pl.pushConstantRangeCount = 1;
        pl.pPushConstantRanges = &push;
        if (vkCreatePipelineLayout(device, &pl, nullptr, &pipelineLayout) != VK_SUCCESS) {
            vkDestroyShaderModule(device, vert, nullptr);
            vkDestroyShaderModule(device, frag, nullptr);
            return false;
        }

        // Two create infos identical but for the blend state; built in one call.
        VkGraphicsPipelineCreateInfo gp{};
        gp.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
        gp.stageCount = 2;
        gp.pStages = stages;
        gp.pVertexInputState = &vin;
        gp.pInputAssemblyState = &ia;
        gp.pViewportState = &vp;
        gp.pRasterizationState = &rs;
        gp.pMultisampleState = &ms;
        gp.pColorBlendState = &cbAdditive;
        gp.pDynamicState = &ds;
        gp.layout = pipelineLayout;
        gp.renderPass = renderPass;
        gp.subpass = 0;
        VkGraphicsPipelineCreateInfo gps[2] = {gp, gp};
        gps[1].pColorBlendState = &cbOver;
        VkPipeline pipelines[2] = {VK_NULL_HANDLE, VK_NULL_HANDLE};
        VkResult res =
            vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 2, gps, nullptr, pipelines);
        vkDestroyShaderModule(device, vert, nullptr);
        vkDestroyShaderModule(device, frag, nullptr);
        if (res != VK_SUCCESS) {
            // A multi-pipeline call may leave some entries non-null on failure;
            // destroy any that succeeded so flyover_stop (which only sees the
            // unset members) can't leak them and vkDestroyDevice runs clean.
            for (VkPipeline p : pipelines) {
                if (p != VK_NULL_HANDLE) vkDestroyPipeline(device, p, nullptr);
            }
            LOGE("pipeline create failed: %d", res);
            return false;
        }
        pipeline = pipelines[0];
        pipelineOver = pipelines[1];
        return true;
    }

    bool createFramebuffers() {
        framebuffers.resize(views.size());
        for (size_t i = 0; i < views.size(); ++i) {
            VkFramebufferCreateInfo ci{};
            ci.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
            ci.renderPass = renderPass;
            ci.attachmentCount = 1;
            ci.pAttachments = &views[i];
            ci.width = extent.width;
            ci.height = extent.height;
            ci.layers = 1;
            VK_CHECK(vkCreateFramebuffer(device, &ci, nullptr, &framebuffers[i]));
        }
        return true;
    }

    bool createCommandBuffers() {
        VkCommandPoolCreateInfo pi{};
        pi.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
        pi.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
        pi.queueFamilyIndex = queueFamily;
        VK_CHECK(vkCreateCommandPool(device, &pi, nullptr, &commandPool));

        commandBuffers.resize(kMaxFramesInFlight);
        VkCommandBufferAllocateInfo ai{};
        ai.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
        ai.commandPool = commandPool;
        ai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        ai.commandBufferCount = kMaxFramesInFlight;
        VK_CHECK(vkAllocateCommandBuffers(device, &ai, commandBuffers.data()));
        return true;
    }

    bool createSyncObjects() {
        imageAvailable.resize(kMaxFramesInFlight);
        inFlight.resize(kMaxFramesInFlight);
        imagesInFlight.assign(images.size(), VK_NULL_HANDLE);

        VkSemaphoreCreateInfo si{};
        si.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
        VkFenceCreateInfo fi{};
        fi.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
        fi.flags = VK_FENCE_CREATE_SIGNALED_BIT;
        for (int i = 0; i < kMaxFramesInFlight; ++i) {
            VK_CHECK(vkCreateSemaphore(device, &si, nullptr, &imageAvailable[i]));
            VK_CHECK(vkCreateFence(device, &fi, nullptr, &inFlight[i]));
        }
        return createRenderFinished();
    }

    // renderFinished is per swapchain IMAGE (signaled by submit, waited by
    // present): a per-frame semaphore could still be pending in a present when
    // the frame index wraps, a WSI reuse hazard. Recreated with the swapchain
    // because the image count can change.
    bool createRenderFinished() {
        for (auto s : renderFinished) vkDestroySemaphore(device, s, nullptr);
        renderFinished.assign(images.size(), VK_NULL_HANDLE);
        VkSemaphoreCreateInfo si{};
        si.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
        for (size_t i = 0; i < images.size(); ++i) {
            VK_CHECK(vkCreateSemaphore(device, &si, nullptr, &renderFinished[i]));
        }
        return true;
    }

    void uploadTrackIfDirty() {
        std::vector<float> local;
        {
            std::lock_guard<std::mutex> lock(trackMutex);
            if (!trackDirty) return;
            local.swap(pendingTrack);
            trackDirty = false;
        }
        // Track changes are rare (a trip selection), so a full idle + rebuild is fine.
        vkDeviceWaitIdle(device);
        if (vertexBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, vertexBuffer, nullptr);
            vertexBuffer = VK_NULL_HANDLE;
        }
        if (vertexMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, vertexMemory, nullptr);
            vertexMemory = VK_NULL_HANDLE;
        }
        vertexCount = 0;
        if (local.size() < kFloatsPerVertex * 2) return;

        VkDeviceSize size = local.size() * sizeof(float);
        VkBufferCreateInfo bi{};
        bi.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
        bi.size = size;
        bi.usage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
        bi.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        if (vkCreateBuffer(device, &bi, nullptr, &vertexBuffer) != VK_SUCCESS) return;

        VkMemoryRequirements req;
        vkGetBufferMemoryRequirements(device, vertexBuffer, &req);
        uint32_t memType = findMemoryType(req.memoryTypeBits,
                                          VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                                              VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        if (memType == UINT32_MAX) {
            vkDestroyBuffer(device, vertexBuffer, nullptr);
            vertexBuffer = VK_NULL_HANDLE;
            return;
        }
        VkMemoryAllocateInfo ai{};
        ai.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
        ai.allocationSize = req.size;
        ai.memoryTypeIndex = memType;
        if (vkAllocateMemory(device, &ai, nullptr, &vertexMemory) != VK_SUCCESS) {
            vkDestroyBuffer(device, vertexBuffer, nullptr);
            vertexBuffer = VK_NULL_HANDLE;
            return;
        }
        if (vkBindBufferMemory(device, vertexBuffer, vertexMemory, 0) != VK_SUCCESS) {
            vkDestroyBuffer(device, vertexBuffer, nullptr);
            vkFreeMemory(device, vertexMemory, nullptr);
            vertexBuffer = VK_NULL_HANDLE;
            vertexMemory = VK_NULL_HANDLE;
            return;
        }
        void *mapped = nullptr;
        if (vkMapMemory(device, vertexMemory, 0, size, 0, &mapped) == VK_SUCCESS) {
            std::memcpy(mapped, local.data(), (size_t) size);
            vkUnmapMemory(device, vertexMemory);
            vertexCount = (uint32_t) (local.size() / kFloatsPerVertex);
        }
    }

    void computePush(PushConstants *pc, float elapsed, float prog, const ThemeSnapshot &theme) {
        // Gentle intro dolly then a steady slow orbit; a pure wireframe reads
        // best when the camera keeps moving so parallax reveals the 3D shape.
        float intro = std::min(1.0f, elapsed / 3.0f);
        float ease = intro * intro * (3.0f - 2.0f * intro);  // smoothstep
        float radius = 3.5f - 0.9f * ease;
        float az = elapsed * kOrbitRate;
        Vec3 center{0.0f, 0.12f, 0.0f};
        Vec3 eye{center.x + radius * std::cos(kElevationRad) * std::sin(az),
                 center.y + radius * std::sin(kElevationRad),
                 center.z + radius * std::cos(kElevationRad) * std::cos(az)};
        float aspect = extent.height > 0 ? (float) extent.width / (float) extent.height : 1.0f;
        Mat4 proj = perspective(45.0f * kPi / 180.0f, aspect, 0.05f, 100.0f);
        Mat4 view = lookAt(eye, center, Vec3{0.0f, 1.0f, 0.0f});
        Mat4 mvp = multiply(proj, view);
        std::memcpy(pc->mvp, mvp.m, sizeof(mvp.m));
        pc->progress = prog;
        pc->time = elapsed;
        pc->aspect = aspect;
        pc->pad = 0.0f;
        pc->head[0] = theme.head[0];
        pc->head[1] = theme.head[1];
        pc->head[2] = theme.head[2];
        pc->head[3] = 0.0f;
    }

    bool recordAndSubmit(uint32_t imageIndex, const PushConstants &pc, const ThemeSnapshot &theme) {
        VkCommandBuffer cmd = commandBuffers[currentFrame];
        vkResetCommandBuffer(cmd, 0);
        VkCommandBufferBeginInfo bi{};
        bi.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
        VK_CHECK(vkBeginCommandBuffer(cmd, &bi));

        VkClearValue clear{};
        // The scene backdrop, pushed from Kotlin via flyover_set_theme so the
        // clear colour follows the light/dark palette (TripScenePalette). The
        // Kotlin TripScenePalette is the single backdrop SSOT.
        clear.color = {{theme.clear[0], theme.clear[1], theme.clear[2], 1.0f}};
        VkRenderPassBeginInfo rp{};
        rp.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
        rp.renderPass = renderPass;
        rp.framebuffer = framebuffers[imageIndex];
        rp.renderArea.extent = extent;
        rp.clearValueCount = 1;
        rp.pClearValues = &clear;
        vkCmdBeginRenderPass(cmd, &rp, VK_SUBPASS_CONTENTS_INLINE);

        VkViewport viewport{0.0f, 0.0f, (float) extent.width, (float) extent.height, 0.0f, 1.0f};
        VkRect2D scissor{{0, 0}, extent};
        vkCmdSetViewport(cmd, 0, 1, &viewport);
        vkCmdSetScissor(cmd, 0, 1, &scissor);

        if (vertexCount > 0 && vertexBuffer != VK_NULL_HANDLE) {
            // Pick the blend model for the current scene: additive on dark, over
            // on light. Both share the layout, so only the bound pipeline differs.
            VkPipeline activePipeline = theme.dark ? pipeline : pipelineOver;
            vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, activePipeline);
            vkCmdPushConstants(cmd, pipelineLayout,
                               VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0,
                               sizeof(PushConstants), &pc);
            VkDeviceSize offset = 0;
            vkCmdBindVertexBuffers(cmd, 0, 1, &vertexBuffer, &offset);
            vkCmdDraw(cmd, vertexCount, 1, 0, 0);
        }

        vkCmdEndRenderPass(cmd);
        VK_CHECK(vkEndCommandBuffer(cmd));

        VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        VkSubmitInfo submit{};
        submit.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
        submit.waitSemaphoreCount = 1;
        submit.pWaitSemaphores = &imageAvailable[currentFrame];
        submit.pWaitDstStageMask = &waitStage;
        submit.commandBufferCount = 1;
        submit.pCommandBuffers = &cmd;
        submit.signalSemaphoreCount = 1;
        submit.pSignalSemaphores = &renderFinished[imageIndex];
        VK_CHECK(vkQueueSubmit(queue, 1, &submit, inFlight[currentFrame]));
        return true;
    }

    void cleanupSwapchain() {
        for (auto fb : framebuffers) vkDestroyFramebuffer(device, fb, nullptr);
        framebuffers.clear();
        for (auto v : views) vkDestroyImageView(device, v, nullptr);
        views.clear();
        if (swapchain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device, swapchain, nullptr);
            swapchain = VK_NULL_HANDLE;
        }
    }

    bool recreateSwapchain() {
        int w = pendingWidth.load();
        int h = pendingHeight.load();
        if (w <= 0 || h <= 0) return false;
        vkDeviceWaitIdle(device);
        cleanupSwapchain();
        if (!createSwapchain(w, h)) return false;
        if (!createFramebuffers()) return false;
        if (!createRenderFinished()) return false;
        imagesInFlight.assign(images.size(), VK_NULL_HANDLE);
        return true;
    }

    // One acquire/record/submit/present cycle. Returns false only on a fatal
    // error (the loop then stops); a stale swapchain triggers a recreate.
    bool drawFrame(float elapsed) {
        // A prior recreate that failed left the swapchain destroyed; never acquire
        // on VK_NULL_HANDLE — ask for another recreate instead.
        if (swapchain == VK_NULL_HANDLE) {
            needsRecreate.store(true);
            return true;
        }
        vkWaitForFences(device, 1, &inFlight[currentFrame], VK_TRUE, UINT64_MAX);

        uint32_t imageIndex = 0;
        VkResult acq = vkAcquireNextImageKHR(device, swapchain, UINT64_MAX,
                                             imageAvailable[currentFrame], VK_NULL_HANDLE,
                                             &imageIndex);
        if (acq == VK_ERROR_OUT_OF_DATE_KHR) {
            needsRecreate.store(true);
            return true;
        }
        if (acq != VK_SUCCESS && acq != VK_SUBOPTIMAL_KHR) {
            LOGE("acquire failed: %d", acq);
            return false;
        }

        if (imagesInFlight[imageIndex] != VK_NULL_HANDLE) {
            vkWaitForFences(device, 1, &imagesInFlight[imageIndex], VK_TRUE, UINT64_MAX);
        }
        imagesInFlight[imageIndex] = inFlight[currentFrame];

        // Snapshot the theme once (7 back-to-back atomic loads) so the whole
        // frame — clear colour, comet head, blend pipeline — is internally
        // consistent even if a theme push lands mid-frame.
        ThemeSnapshot theme{
            {clearR.load(), clearG.load(), clearB.load()},
            {headR.load(), headG.load(), headB.load()},
            themeDark.load(),
        };

        PushConstants pc{};
        computePush(&pc, elapsed, progress.load(), theme);

        vkResetFences(device, 1, &inFlight[currentFrame]);
        if (!recordAndSubmit(imageIndex, pc, theme)) return false;

        VkPresentInfoKHR present{};
        present.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
        present.waitSemaphoreCount = 1;
        present.pWaitSemaphores = &renderFinished[imageIndex];
        present.swapchainCount = 1;
        present.pSwapchains = &swapchain;
        present.pImageIndices = &imageIndex;
        VkResult pres = vkQueuePresentKHR(queue, &present);
        if (pres == VK_ERROR_OUT_OF_DATE_KHR || pres == VK_SUBOPTIMAL_KHR) {
            needsRecreate.store(true);
        } else if (pres != VK_SUCCESS) {
            LOGE("present failed: %d", pres);
            return false;
        }
        currentFrame = (currentFrame + 1) % kMaxFramesInFlight;
        return true;
    }

    void renderLoop() {
        auto start = std::chrono::steady_clock::now();
        while (running.load()) {
            if (needsRecreate.exchange(false)) {
                if (!recreateSwapchain()) {
                    // Re-arm so the next iteration retries instead of drawing on
                    // the swapchain cleanupSwapchain() already destroyed.
                    needsRecreate.store(true);
                    std::this_thread::sleep_for(std::chrono::milliseconds(16));
                    continue;
                }
            }
            uploadTrackIfDirty();

            auto now = std::chrono::steady_clock::now();
            float elapsed = std::chrono::duration<float>(now - start).count();
            // progress is driven externally (the panel's frame clock); the render
            // thread only self-drives the always-on camera orbit via elapsed.

            if (!drawFrame(elapsed)) {
                running.store(false);
                break;
            }
        }
        vkDeviceWaitIdle(device);
    }
};

// ---- C API -----------------------------------------------------------------

FlyoverRenderer *flyover_create() {
    auto *r = new FlyoverRenderer();
    if (!r->createInstance()) {
        delete r;
        return nullptr;
    }
    return r;
}

bool flyover_start(FlyoverRenderer *r, ANativeWindow *window, int width, int height) {
    if (!r || !window) return false;
    r->pendingWidth.store(width);
    r->pendingHeight.store(height);
    r->window = window;  // createSurface reads r->window
    bool ok = r->createSurface() && r->pickDevice() && r->createDevice() &&
              r->createSwapchain(width, height) && r->createRenderPass() &&
              r->createPipeline() && r->createFramebuffers() &&
              r->createCommandBuffers() && r->createSyncObjects();
    if (!ok) {
        // The JNI bridge releases the window ref it acquired when start fails, so
        // clear it here to keep flyover_stop from releasing the same ref again.
        // The partially-built Vk objects are cleaned up by the later flyover_stop.
        r->window = nullptr;
        return false;
    }
    r->running.store(true);
    r->renderThread = std::thread([r]() { r->renderLoop(); });
    LOGI("flyover started %dx%d", width, height);
    return true;
}

void flyover_set_track(FlyoverRenderer *r, const float *data, int floatCount) {
    if (!r || !data || floatCount <= 0) return;
    std::lock_guard<std::mutex> lock(r->trackMutex);
    r->pendingTrack.assign(data, data + floatCount);
    r->trackDirty = true;
}

void flyover_set_progress(FlyoverRenderer *r, float progress) {
    if (!r) return;
    // Negated comparisons so NaN clamps to 0 (a raw NaN would blank the reveal).
    float clamped = !(progress >= 0.0f) ? 0.0f : (progress > 1.0f ? 1.0f : progress);
    r->progress.store(clamped);
}

void flyover_set_theme(FlyoverRenderer *r, float bgR, float bgG, float bgB, float headR,
                       float headG, float headB, bool isDark) {
    if (!r) return;
    r->clearR.store(bgR);
    r->clearG.store(bgG);
    r->clearB.store(bgB);
    r->headR.store(headR);
    r->headG.store(headG);
    r->headB.store(headB);
    r->themeDark.store(isDark);
}

bool flyover_is_running(FlyoverRenderer *r) { return r && r->running.load(); }

void flyover_resize(FlyoverRenderer *r, int width, int height) {
    if (!r) return;
    r->pendingWidth.store(width);
    r->pendingHeight.store(height);
    r->needsRecreate.store(true);
}

void flyover_stop(FlyoverRenderer *r) {
    if (!r) return;
    // Join unconditionally: the render thread also clears `running` itself on a
    // fatal drawFrame error, so an exchange-guarded join would skip a still-
    // joinable thread and the later thread destructor would std::terminate.
    r->running.store(false);
    if (r->renderThread.joinable()) r->renderThread.join();
    if (r->device != VK_NULL_HANDLE) {
        vkDeviceWaitIdle(r->device);
        if (r->vertexBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(r->device, r->vertexBuffer, nullptr);
            r->vertexBuffer = VK_NULL_HANDLE;
        }
        if (r->vertexMemory != VK_NULL_HANDLE) {
            vkFreeMemory(r->device, r->vertexMemory, nullptr);
            r->vertexMemory = VK_NULL_HANDLE;
        }
        for (auto s : r->imageAvailable) vkDestroySemaphore(r->device, s, nullptr);
        for (auto s : r->renderFinished) vkDestroySemaphore(r->device, s, nullptr);
        for (auto f : r->inFlight) vkDestroyFence(r->device, f, nullptr);
        r->imageAvailable.clear();
        r->renderFinished.clear();
        r->inFlight.clear();
        if (r->commandPool != VK_NULL_HANDLE) {
            vkDestroyCommandPool(r->device, r->commandPool, nullptr);
            r->commandPool = VK_NULL_HANDLE;
        }
        if (r->pipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(r->device, r->pipeline, nullptr);
            r->pipeline = VK_NULL_HANDLE;
        }
        if (r->pipelineOver != VK_NULL_HANDLE) {
            vkDestroyPipeline(r->device, r->pipelineOver, nullptr);
            r->pipelineOver = VK_NULL_HANDLE;
        }
        if (r->pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(r->device, r->pipelineLayout, nullptr);
            r->pipelineLayout = VK_NULL_HANDLE;
        }
        if (r->renderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(r->device, r->renderPass, nullptr);
            r->renderPass = VK_NULL_HANDLE;
        }
        r->cleanupSwapchain();
        vkDestroyDevice(r->device, nullptr);
        r->device = VK_NULL_HANDLE;
    }
    if (r->surface != VK_NULL_HANDLE) {
        vkDestroySurfaceKHR(r->instance, r->surface, nullptr);
        r->surface = VK_NULL_HANDLE;
    }
    if (r->window) {
        ANativeWindow_release(r->window);
        r->window = nullptr;
    }
}

void flyover_destroy(FlyoverRenderer *r) {
    if (!r) return;
    flyover_stop(r);
    if (r->instance != VK_NULL_HANDLE) {
        vkDestroyInstance(r->instance, nullptr);
        r->instance = VK_NULL_HANDLE;
    }
    delete r;
}
