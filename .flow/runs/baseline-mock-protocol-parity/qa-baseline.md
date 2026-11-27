# Baseline: committed profile rule counts

Rule lines per class prefix (all lines referencing the prefix, methods and class entries). Source: git show of app/src/main/generated/baselineProfiles/{baseline,startup}-prof.txt. Method: grep of 'L<prefix>/' lines; no device, no API. The phosphoricons row is 0 because the package is com/adamglin (see the Phosphor section below).

| Prefix | 08f50b81 baseline | 08f50b81 startup | 5cd3e37e baseline | 5cd3e37e startup |
|---|---|---|---|---|
| total lines | 47737 | 31786 | 46352 | 31146 |
| okhttp3/internal/http1 | 0 | 0 | 51 | 45 |
| okhttp3/internal/http2 | 292 | 292 | 316 | 298 |
| okhttp3/internal/tls | 32 | 32 | 30 | 30 |
| kotlinx/serialization/internal | 165 | 155 | 234 | 216 |
| kotlinx/serialization/descriptors | 137 | 125 | 203 | 179 |
| kotlinx/serialization/json | 300 | 292 | 304 | 299 |
| phosphoricons | 0 | 0 | 0 | 0 |
| coil3/network | 221 | 221 | 158 | 128 |
| coil3/decode | 148 | 148 | 83 | 83 |
| androidx/media3/exoplayer/hls | 387 | 1 | 376 | 1 |

## Phosphor icons (package com/adamglin)

Per-file counts (lines containing Lcom/adamglin/, and Eye / BookmarkSimple subsets):



```
08f50b81-baseline.txt phosphor(com/adamglin)=54 filled=0 eye=3 bookmarkSimple=1
08f50b81-startup.txt phosphor(com/adamglin)=42 filled=0 eye=0 bookmarkSimple=0
5cd3e37e-baseline.txt phosphor(com/adamglin)=72 filled=6 eye=6 bookmarkSimple=2
5cd3e37e-startup.txt phosphor(com/adamglin)=42 filled=0 eye=0 bookmarkSimple=0
HSPLcom/adamglin/phosphoricons/duotone/BookmarksimpleKt;->getBookmarkSimple(Lcom/adamglin/phosphoricons/DuotoneGroup;)Landroidx/compose/ui/graphics/vector/ImageVector;
Lcom/adamglin/phosphoricons/duotone/EyeKt;
HSPLcom/adamglin/phosphoricons/duotone/EyeKt;-><clinit>()V
HSPLcom/adamglin/phosphoricons/duotone/EyeKt;->getEye(Lcom/adamglin/phosphoricons/DuotoneGroup;)Landroidx/compose/ui/graphics/vector/ImageVector;```
