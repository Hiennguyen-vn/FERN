```markdown
# Design System Specification: The Architectural ERP

## 1. Overview & Creative North Star
The North Star for this design system is **"The Precision Atelier."** 

In the chaotic world of Food & Beverage enterprise management—where thin margins meet high-speed operations—this system acts as a calming, authoritative force. We are moving away from the "cluttered spreadsheet" aesthetic typical of legacy ERPs. Instead, we embrace a **High-End Editorial** approach: high-density data wrapped in a premium, layered interface. 

The design breaks the "software template" look through **Tonal Architecture**. By eliminating traditional lines and borders, we create an environment that feels expansive and breathable, even when displaying hundreds of data points. We prioritize intentional asymmetry in dashboard layouts to guide the eye toward "Primary Action Zones" (like live POS feeds) while keeping secondary back-office controls elegantly recessed.

---

## 2. Colors: The Tonal Spectrum
This system utilizes a sophisticated palette where color is used for **semantic intent** rather than mere decoration.

### The "No-Line" Rule
**Borders are prohibited for sectioning.** To define boundaries, designers must use background color shifts. 
- A `surface-container-low` section should sit directly on a `surface` background. 
- Content blocks are separated by white space or a shift to `surface-container-highest`.

### Surface Hierarchy & Nesting
Treat the UI as a physical stack of materials.
- **Base Layer:** `surface` (#f7f9fb) for the main application background.
- **Mid Layer:** `surface-container-low` (#f2f4f6) for sidebar navigation or secondary utility panels.
- **Top Layer:** `surface-container-lowest` (#ffffff) for primary data cards and interactive modules. This creates a "lifted" effect without heavy shadows.

### The "Glass & Gradient" Rule
For high-level summaries (e.g., Daily Revenue cards), use **Glassmorphism**. Apply `surface-container-lowest` at 80% opacity with a `24px` backdrop blur. 
- **Signature Texture:** Primary CTAs should use a subtle linear gradient from `primary` (#27389a) to `primary_container` (#4151b3) at a 135-degree angle to provide a "jeweled" depth that flat indigo cannot achieve.

---

## 3. Typography: Authority & Clarity
We utilize a dual-font strategy to balance brand personality with data utility.

*   **Display & Headlines (Manrope):** Chosen for its geometric, modern structure. Use `display-md` and `headline-sm` for dashboard summaries and section headers. The wider aperture of Manrope conveys a sense of modern "Scale."
*   **Interface & Data (Inter):** The workhorse. Inter is used for all `body` and `label` styles. Its tall x-height and narrow tracking make it perfect for the high-density tables required in inventory and F&B procurement views.

**Visual Hierarchy Tip:** Use `label-sm` in `on_surface_variant` (#454652) for "All Caps" metadata to create a sophisticated, archival feel without distracting from primary data.

---

## 4. Elevation & Depth: Tonal Layering
Traditional ERPs feel "flat." This system feels "architectural."

*   **The Layering Principle:** Depth is achieved by "stacking" the surface tiers. A `surface-container-highest` panel on a `surface` background provides all the separation required for a side-drawer or modal.
*   **Ambient Shadows:** For floating elements like POS Modals, use a shadow: `0px 12px 32px rgba(25, 28, 30, 0.06)`. This uses the `on_surface` color as a tint, mimicking natural light.
*   **The "Ghost Border" Fallback:** If a border is required for accessibility (e.g., in a high-glare POS environment), use `outline_variant` at 15% opacity. Never use 100% opaque lines.

---

## 5. Components: Enterprise Primitives

### Action Elements
*   **Buttons:** Primary buttons use the Signature Gradient. Secondary buttons use `secondary_container` with `on_secondary_container` text. For Back-office, use `md` (0.375rem) roundedness; for POS, use `xl` (0.75rem) for a friendlier touch-target.
*   **Sticky Action Areas:** Footer actions in long forms must use a `surface_bright` background with a `surface_tint` (10% opacity) top "Ghost Border."

### Data & Status
*   **Status Badges:** Use `tertiary_container` for Success and `error_container` for Danger. Use `label-md` bold text. Do not use borders; use high-contrast text against the tinted background.
*   **Timeline Indicators:** Vertical nodes using `primary` for completed states and `outline` for pending. Connect nodes with a 2px `surface_variant` vertical track.
*   **Cards & Lists:** **No Dividers.** Use `spacing-4` (0.9rem) of vertical white space or a subtle shift to `surface_container_low` on hover to separate list items.

### Restricted States
*   **Readonly:** Background shifts to `surface_dim`. Text remains `on_surface` but at 60% opacity.
*   **Masked (PII):** Use a `blur(4px)` effect over the text instead of just asterisks. This maintains the "Glass" aesthetic while securing data.
*   **Permission Denied:** Replace the interactive element with a `locked` icon in `outline` color. On hover, show a tooltip in `inverse_surface` explaining the required permission tier.

---

## 6. Do’s and Don'ts

### Do
*   **DO** use `surface-container-highest` for "Active" states in navigation.
*   **DO** leverage the `spacing-1` and `spacing-2` tokens for high-density tables to maximize information density without losing legibility.
*   **DO** use `tertiary` (#004e33) for all "Growth" or "Profit" related metrics—it provides a more sophisticated "F&B Green" than standard neon variants.

### Don't
*   **DON'T** use black (#000000) for text. Always use `on_surface` (#191c1e) to maintain the tonal softness of the system.
*   **DON'T** use cards inside cards. Instead, use a background color shift (e.g., a `surface-container-lowest` card containing `surface-container-high` data regions).
*   **DON'T** use icons alone for primary navigation. Always accompany them with `label-md` typography to ensure the system remains professional and unambiguous.

---

## 7. Contextual Adaptation: POS vs. Back-Office

| Feature | POS (Front of House) | Back-Office (Admin) |
| :--- | :--- | :--- |
| **Touch Target** | Min 44px (using `spacing-12`) | Min 32px (using `spacing-8`) |
| **Roundedness** | `xl` (0.75rem) for soft edges | `md` (0.375rem) for precision |
| **Density** | `body-lg` / `title-md` | `body-sm` / `label-md` |
| **Elevation** | Higher contrast, glass headers | Low contrast, tonal nesting |

By following these guidelines, you will create a system that feels like a bespoke tool for culinary professionals—not just another piece of enterprise software. Keep it layered, keep it light, and respect the "No-Line" rule.```