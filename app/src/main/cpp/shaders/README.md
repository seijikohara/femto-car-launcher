# Trip-flyover shaders

`line.vert` / `line.frag` are the GLSL sources for the native Vulkan wireframe
pipeline. They are **precompiled to SPIR-V and committed** as C headers under
`../generated/` (`line_vert_spv.h`, `line_frag_spv.h`), so the app build has no
`glslc` dependency and CI needs only the pinned NDK + CMake.

## Regenerating after editing a shader

Use the `glslc` bundled with the pinned NDK (see `ndk` in
`gradle/libs.versions.toml`), then re-embed as `uint32_t` arrays:

```sh
NDK="$ANDROID_HOME/ndk/<version>"
GLSLC="$NDK/shader-tools/$(ls "$NDK/shader-tools")/glslc"

for stage in vert frag; do
  "$GLSLC" -O "line.$stage" -o "/tmp/line.$stage.spv"
done

python3 - <<'PY'
import struct
for stage, sym in (("vert", "kLineVertSpv"), ("frag", "kLineFragSpv")):
    data = open(f"/tmp/line.{stage}.spv", "rb").read()
    words = struct.unpack(f"<{len(data)//4}I", data)
    with open(f"../generated/line_{stage}_spv.h", "w") as f:
        f.write(f"// Generated from shaders/line.{stage} by glslc. Do not edit.\n")
        f.write("#pragma once\n#include <cstdint>\n#include <cstddef>\n")
        f.write(f"static const uint32_t {sym}[] = {{\n")
        for i in range(0, len(words), 8):
            f.write("  " + ",".join(f"0x{w:08x}" for w in words[i:i+8]) + ",\n")
        f.write("};\n")
        f.write(f"static const size_t {sym}_size = sizeof({sym});\n")
PY
```

Commit the regenerated `generated/*.h` alongside the shader edit.
