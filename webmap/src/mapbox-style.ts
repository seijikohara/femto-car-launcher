// Pure Mapbox style helpers, mirrored from the host's MapboxStyle enum. The host
// sends the resolved style id; this maps it to a mapbox:// URL and owns the
// traffic overlay spec. Kept side-effect-free so it is unit-testable in node.
const STYLE_URLS: Record<string, string> = {
	standard: "mapbox://styles/mapbox/standard",
	"satellite-streets-v12": "mapbox://styles/mapbox/satellite-streets-v12",
	"streets-v12": "mapbox://styles/mapbox/streets-v12",
};

export function mapboxStyleUrl(styleId: string): string {
	return STYLE_URLS[styleId] ?? STYLE_URLS.standard;
}

export const TRAFFIC_SOURCE_ID = "mapbox-traffic";
export const TRAFFIC_LAYER_ID = "femto-traffic";

// Real-time congestion as a line layer over the mapbox-traffic-v1 vector source.
// `slot: "middle"` keeps it below 3D buildings/labels in the Standard fragment
// style (Mapbox v3 slot API); harmless on the non-fragment Streets/Satellite
// styles. Colours follow Mapbox's congestion classes.
export function trafficLayerSpec(): Record<string, unknown> {
	return {
		id: TRAFFIC_LAYER_ID,
		type: "line",
		source: TRAFFIC_SOURCE_ID,
		"source-layer": "traffic",
		slot: "middle",
		paint: {
			"line-width": 2,
			"line-color": [
				"match",
				["get", "congestion"],
				"low",
				"#4caf50",
				"moderate",
				"#ffb300",
				"heavy",
				"#e53935",
				"severe",
				"#8e24aa",
				"#9e9e9e",
			],
		},
	};
}

export const TRAFFIC_SOURCE_SPEC: Record<string, unknown> = {
	type: "vector",
	url: "mapbox://mapbox.mapbox-traffic-v1",
};
