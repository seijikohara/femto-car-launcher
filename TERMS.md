# Terms of Service

**Effective date: 2026-09-18**

These terms apply to Femto Car Launcher ("the app"), an Android home launcher
distributed as a signed APK from this repository's releases. Installing or using
the app means accepting them. The app is provided free of charge, requires no
account, and carries no advertising SDK and no analytics SDK of its own. The one
exception is the optional Google Maps provider, whose JavaScript API collects
usage data for Google when you enable it; the [Privacy Policy](PRIVACY.md)
describes that and everything else the app does with data.

## The app is provided as-is

The app is provided **as-is and without warranty of any kind**, to the extent
applicable law allows. It is a hobby project with no service-level commitment:
releases may change or stop, and the third-party services it draws on may change
their terms, their availability, or their pricing at any time.

## Driving is your responsibility

The app displays information on a screen in a vehicle. It is not a navigation
system, not a driver-assistance system, and not a substitute for the vehicle's
own instruments. You are responsible for operating your vehicle safely and for
complying with the law where you drive, including any law governing screens and
their placement in a vehicle.

## Map and weather providers

The app renders maps from a provider you select in **Settings → Map**.

- **OpenStreetMap (default).** Map data © OpenStreetMap contributors, available
  under the [Open Database License](https://opendatacommons.org/licenses/odbl/).
  Tiles and the style derive from OpenMapTiles and are served by OpenFreeMap;
  terrain elevation comes from Mapterhorn. The full credits and licence texts
  are in the app under **Settings → System → Open source licenses**.
- **Custom style URL.** If you point the OpenStreetMap provider at a hosted
  map style of your own choosing (Settings → Appearance → Map color), the map
  loads that style as its publisher serves it and shows the credits the style
  declares in place of the app's own. Your use of that style and its tiles is
  between you and its publisher, under their terms.
- **Weather.** Forecast data from MET Norway, the Norwegian Meteorological
  Institute, under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

### Google Maps

The app includes **Google Maps features and content**. They are inactive until
you enter your own Google Maps Platform API key in **Settings → Map**; the app
ships no key of its own and adds no fees, and any usage bills the Google Cloud
account behind the key you enter.

When the Google Maps map provider is in use, your use of Google Maps features
and content is subject to the then-current versions of the:

1. [Google Maps End User Additional Terms of Service](https://maps.google.com/help/terms_maps/)
2. [Google Privacy Policy](https://policies.google.com/privacy)

Your own key is additionally governed by the
[Google Maps Platform Terms of Service](https://cloud.google.com/maps-platform/terms),
which you accept with Google when you create the key. Those terms restrict what
a Google Maps key may be used for; reading them before attaching a billing
account is worth the few minutes it takes. A link to them sits beside the key
field in Settings.

**Prohibited Territories.** The Google Maps Platform Terms of Service bar
distributing or marketing an application that uses the Google Maps Core Services
in the territories on Google's
[Prohibited Territories](https://cloud.google.com/maps-platform/terms/maps-prohibited-territories)
list. **The Google Maps map provider is therefore not offered in those
territories**, and the app must not be installed there with that provider
enabled. The default OpenStreetMap provider is unaffected. Distribution is
through GitHub Releases and is subject to GitHub's own trade controls, which do
not cover every territory on Google's list — so this restriction relies in part
on you.

## Open-source components

The app bundles third-party open-source components. Each is credited with its
licence in the app under **Settings → System → Open source licenses**. Nothing
in these terms limits any right granted to you by those licences.

## Changes

These terms may change with any release. The effective date above marks the
current version; the history is in this repository.

## Contact

Open an issue at
<https://github.com/seijikohara/femto-car-launcher/issues>.
