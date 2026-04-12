# Dual-Channel Card Images Design

**Goal:** Display cropped card artwork in the info banner and full card images on hand/field/inspector elements, with polite rate limiting.

## Image Channels

| Channel | ygoprodeck path | Disk cache path | ResourceLocation | Used by |
|---------|----------------|-----------------|------------------|---------|
| Card art (cropped, 624x624) | `cards_cropped/{code}.jpg` | `cache/images/art/{code}.png` | `duelcraft:dynamic/art_{code}` | `showCardInfo()` banner image |
| Card texture (full, 168x246) | `cards_small/{code}.jpg` | `cache/images/cards/{code}.png` | `duelcraft:dynamic/card_{code}` | Hand cards, field zones, inspector entries |

## CardImageManager Changes

The manager grows a second channel. Both channels share:
- The 2-thread executor pool
- The HttpClient instance
- The JPEG-to-PNG conversion logic (`convertToPng`)
- The DynamicTexture registration pattern

Each channel has its own:
- `ConcurrentHashMap<Integer, ResourceLocation>` texture cache
- `Set<Integer>` for loading and failed tracking
- Cache subdirectory on disk
- URL prefix derived from `Config.CARD_IMAGE_BASE_URL`

### URL Derivation

The cropped art URL is derived from `CARD_IMAGE_BASE_URL` by replacing the last path segment. If the configured base URL is `https://images.ygoprodeck.com/images/cards_small/`, the art URL becomes `https://images.ygoprodeck.com/images/cards_cropped/`. No new config entry.

### Public API

```java
/** Cropped artwork for the info banner. Returns null while loading. */
public ResourceLocation getCardArt(int code)

/** Full card image for hand/field/inspector. Returns null while loading. */
public ResourceLocation getCardTexture(int code)
```

### Rate Limiting

A 50ms delay between HTTP requests, enforced via a shared timestamp:

```java
private volatile long lastRequestTime = 0;

private void throttle() throws InterruptedException {
    long now = System.currentTimeMillis();
    long elapsed = now - lastRequestTime;
    if (elapsed < 50) {
        Thread.sleep(50 - elapsed);
    }
    lastRequestTime = System.currentTimeMillis();
}
```

Called before each HTTP request in the download methods. Combined with the 2-thread pool, this means at most ~20 requests/second with 2 in flight.

## UI Wiring

| Location | Current behavior | New behavior |
|----------|-----------------|-------------|
| `showCardInfo()` banner image | `getCardTexture(code)` via `sprite()` | `getCardArt(code)` via `sprite()` |
| `rebuildHand()` card slots | Text label with card name | `sprite()` background from `getCardTexture(code)`, keep name label as overlay |
| `refreshZoneSlot()` field zones | Text label with card name | `sprite()` background from `getCardTexture(code)`, keep name label as overlay |
| `handlePileClick()` inspector entries | Text label with card name | `sprite()` background from `getCardTexture(code)`, keep name label as overlay |

For hand/field/inspector elements: set the card image as the element's background via `lss("background", "sprite(...)")`. The existing text label remains as an overlay on top (visible when image hasn't loaded yet, gradually replaced by art). LDLib2's GPU rendering handles downscaling automatically.

## Scaling

LDLib2's `sprite()` stretches the texture to fill the element bounds via GPU bilinear filtering. No Java-side downscaling needed.

- Card art (624x624) in 150x150 banner area: 1:1 aspect, clean downscale
- Full card (168x246) in 24x32 hand slot: slight aspect stretch (0.683 vs 0.75), negligible at this size
- Full card (168x246) in field zone: the `card-image` child element inside the square slot must use `aspect-rate: 0.75` and `height: 100%` so the card keeps its natural proportions within the square zone. The square slot provides the clickable/hoverable area; the card visual inside it stays card-shaped.

## Files Changed

- Modify: `CardImageManager.java` — add second channel, rate limiting, `getCardArt()` method
- Modify: `LDLibDuelScreen.java` — wire `getCardArt()` into banner, `getCardTexture()` into hand/field/inspector as backgrounds
