import { useEffect, useState, useSyncExternalStore } from "react";
import { MenuIcon, MonitorIcon, MoonIcon, SunIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button, buttonVariants } from "@/components/ui/button";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuRadioGroup,
    DropdownMenuRadioItem,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
    Sheet,
    SheetContent,
    SheetHeader,
    SheetTitle,
    SheetTrigger,
} from "@/components/ui/sheet";
import {
    applyTheme,
    readThemeMode,
    THEME_MODES,
    type ThemeMode,
} from "@/lib/theme";

export interface NavItem {
    name: string;
    /** Base-prefixed by the Astro mount. */
    href: string;
    active: boolean;
}

export interface SiteHeaderProps {
    brand: { name: string; href: string; logoSrc: string };
    items: NavItem[];
    showThemeToggle: boolean;
}

const MODE_LABEL: Record<ThemeMode, string> = {
    system: "System",
    light: "Light",
    dark: "Dark",
};

function ModeIcon({
    mode,
    className,
}: {
    mode: ThemeMode;
    className?: string;
}) {
    if (mode === "light")
        return <SunIcon className={className} aria-hidden="true" />;
    if (mode === "dark")
        return <MoonIcon className={className} aria-hidden="true" />;
    return <MonitorIcon className={className} aria-hidden="true" />;
}

// The persisted theme mode is genuinely external state (localStorage), and
// the DropdownMenu's selection plus the trigger's icon/label both need to
// stay in sync with it, so useSyncExternalStore is the correct primitive —
// not useState+useEffect, which would need a synchronous setState call
// inside the effect (oxlint's react/set-state-in-effect). getServerSnapshot
// returns the same "system" placeholder readThemeMode() itself falls back to
// server-side (there is no localStorage during Astro's SSR render), so the
// two stay identical through hydration; React re-renders with the real
// client value right after, no flash-of-wrong-mode and no mismatch warning.
const themeListeners = new Set<() => void>();
function subscribeToTheme(onStoreChange: () => void): () => void {
    themeListeners.add(onStoreChange);
    return () => themeListeners.delete(onStoreChange);
}
function notifyThemeChange(): void {
    themeListeners.forEach((listener) => listener());
}
function getServerThemeMode(): ThemeMode {
    return "system";
}

function ThemeMenu() {
    const mode = useSyncExternalStore(
        subscribeToTheme,
        readThemeMode,
        getServerThemeMode,
    );
    const choose = (next: string) => {
        if (!(THEME_MODES as readonly string[]).includes(next)) return;
        applyTheme(next as ThemeMode);
        notifyThemeChange();
    };

    // Controlled so it can be force-closed below. Its popup portals into
    // <body>, same as the Sheet's, and the view-transition swap replaces
    // that body — left uncontrolled, Base UI would keep "open" state for a
    // menu that no longer exists in the incoming document.
    const [open, setOpen] = useState(false);
    useEffect(() => {
        const close = () => setOpen(false);
        document.addEventListener("astro:before-swap", close);
        return () => document.removeEventListener("astro:before-swap", close);
    }, []);

    return (
        <DropdownMenu open={open} onOpenChange={setOpen}>
            <DropdownMenuTrigger
                render={
                    <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`Theme: ${MODE_LABEL[mode]}`}
                    />
                }
            >
                <ModeIcon mode={mode} className="size-5" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
                <DropdownMenuRadioGroup value={mode} onValueChange={choose}>
                    {THEME_MODES.map((option) => (
                        // Base UI's Menu.RadioItem defaults closeOnClick to
                        // false; a theme choice is a completed action, so
                        // close the menu the way a mode toggle conventionally
                        // does.
                        <DropdownMenuRadioItem
                            key={option}
                            value={option}
                            closeOnClick
                        >
                            <ModeIcon mode={option} className="size-4" />
                            {MODE_LABEL[option]}
                        </DropdownMenuRadioItem>
                    ))}
                </DropdownMenuRadioGroup>
            </DropdownMenuContent>
        </DropdownMenu>
    );
}

function NavLinks({
    items,
    className,
    linkClassName,
    onNavigate,
}: {
    items: NavItem[];
    className?: string;
    /** Per-instance override: the mobile Sheet wants a taller, full-width tap target than the desktop nav's compact "sm" buttons. */
    linkClassName?: string;
    onNavigate?: () => void;
}) {
    return (
        <ul className={cn("flex gap-1", className)}>
            {items.map((item) => (
                <li key={item.href}>
                    <a
                        href={item.href}
                        aria-current={item.active ? "page" : undefined}
                        onClick={onNavigate}
                        className={cn(
                            buttonVariants({ variant: "ghost", size: "sm" }),
                            "text-muted-foreground",
                            item.active && "bg-accent text-foreground",
                            linkClassName,
                        )}
                    >
                        {item.name}
                    </a>
                </li>
            ))}
        </ul>
    );
}

export default function SiteHeader({
    brand,
    items,
    showThemeToggle,
}: SiteHeaderProps) {
    const [menuOpen, setMenuOpen] = useState(false);

    // The sheet renders into document.body through a portal; the view-transition
    // swap replaces that body, so close before it happens or React would keep
    // "open" state for a panel that no longer exists.
    useEffect(() => {
        const close = () => setMenuOpen(false);
        document.addEventListener("astro:before-swap", close);
        return () => document.removeEventListener("astro:before-swap", close);
    }, []);

    return (
        <header className="sticky top-0 z-40 border-b border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
            <div className="mx-auto flex h-16 w-full max-w-6xl items-center gap-4 px-4 sm:px-6">
                {/* No aria-label here: the visible brand text below already gives this
                    link an accessible name ("Femto Car Launcher"), and the logo is
                    decorative (alt=""). An aria-label would override that name instead
                    of just supplementing it, hiding the brand text from assistive tech. */}
                <a
                    href={brand.href}
                    className="flex items-center gap-2 font-semibold"
                >
                    <img
                        src={brand.logoSrc}
                        alt=""
                        width={28}
                        height={28}
                        className="size-7 rounded-md"
                    />
                    <span>{brand.name}</span>
                </a>
                <nav
                    aria-label="Main navigation"
                    className="ml-auto hidden md:block"
                >
                    <NavLinks items={items} />
                </nav>
                <div className="ml-auto flex items-center gap-1 md:ml-2">
                    {showThemeToggle && <ThemeMenu />}
                    <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
                        <SheetTrigger
                            render={
                                <Button
                                    variant="ghost"
                                    size="icon"
                                    className="md:hidden"
                                    aria-label="Open menu"
                                />
                            }
                        >
                            <MenuIcon className="size-5" aria-hidden="true" />
                        </SheetTrigger>
                        <SheetContent side="right">
                            <SheetHeader>
                                <SheetTitle>Menu</SheetTitle>
                            </SheetHeader>
                            <nav
                                aria-label="Mobile navigation"
                                className="px-4"
                            >
                                <NavLinks
                                    items={items}
                                    className="flex-col"
                                    linkClassName="h-11 w-full justify-start text-base"
                                    onNavigate={() => setMenuOpen(false)}
                                />
                            </nav>
                        </SheetContent>
                    </Sheet>
                </div>
            </div>
        </header>
    );
}
