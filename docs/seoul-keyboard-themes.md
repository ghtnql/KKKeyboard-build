# Seoul keyboard themes

The keyboard supports Seoul Day and Seoul Night alongside System, Light, and
Dark. The main app stores the selection; Android's input method reads its local
preference, and the iOS keyboard extension reads the app's App Group preference.
The key layout and height do not change. Both Seoul themes are free.

Runtime artwork is in `shared/themes/`: wide 1440 × 480 images for landscape
and 960 × 600 crops for portrait. Seoul Night in portrait uses the left
section of the wide image so Jamsil's Lotte World Tower stays visible in the
keyboard's narrower frame. Full resolution source
art is in `design_mockups/09_Seoul_Day_Keyboard.png` and
`design_mockups/10_Seoul_Night_Keyboard.png` (2048 × 683). The generated
watercolor includes the Han River, Seoul skyline, Namsan, and N Seoul Tower.
The night artwork was derived from the day artwork to preserve the composition.

Image generation used the built-in `image_gen` tool. Prompt set:

- Day: “Original low-contrast panoramic Seoul daytime watercolor for a mobile
  keyboard background; N Seoul Tower on Namsan, layered skyline and Han River
  along the lower third, quiet pale sky above, no text or keyboard UI.”
- Night edit: “Transform the same scene to a muted indigo blue-hour night with
  restrained warm city lights; preserve skyline, bridge, trees, tower position,
  framing, and watercolor texture; no text or keyboard UI.”

The artwork sits behind the existing keys with translucent key surfaces.
It is not a separate decorative banner, and keyboard input does not depend on
image loading; a solid theme color is used if an asset is unavailable.
