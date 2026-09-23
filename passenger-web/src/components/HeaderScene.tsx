import { memo } from 'react'

/**
 * Decorative animated Karnataka bus header scene.
 *
 * A single static SVG/CSS layer that sits behind the functional header UI:
 * landscape silhouette, road strip, and a CSS-animated KSRTC-style bus moving
 * left → right with a fading dust trail. Purely visual — no state, no timers,
 * no React re-renders, pointer-events none, and inert for screen readers.
 *
 * Motion is implemented with transform/opacity keyframes (translate3d) so it
 * never triggers layout or repaint storms, and it is fully disabled under
 * `prefers-reduced-motion` (a static parked bus + road remain visible).
 */
const HeaderScene = memo(function HeaderScene() {
  return (
    <div className="header-scene" aria-hidden="true">
      <span className="scene-sky" />
      <span className="scene-sun" />

      <svg
        className="scene-landscape"
        viewBox="0 0 1200 210"
        preserveAspectRatio="xMidYMax slice"
        role="presentation"
        focusable="false"
      >
        {/* Distant hills */}
        <path
          d="M0,210 C90,150 170,118 260,150 C350,180 420,128 530,154 C640,180 700,122 810,150 C920,178 1000,132 1200,158 L1200,210 Z"
          fill="#0a6044"
          opacity="0.5"
        />
        {/* Nearer hills */}
        <path
          d="M0,210 C70,186 150,170 250,190 C360,208 430,182 545,198 C660,214 760,180 880,202 C990,220 1100,196 1200,210 L1200,210 Z"
          fill="#07402b"
          opacity="0.85"
        />
        {/* Rounded roadside trees */}
        <g fill="#053a28">
          <ellipse cx="92" cy="188" rx="18" ry="15" />
          <rect x="88" y="200" width="8" height="14" fill="#03301f" />
          <ellipse cx="262" cy="196" rx="14" ry="12" />
          <rect x="259" y="206" width="6" height="10" fill="#03301f" />
          <ellipse cx="640" cy="188" rx="16" ry="13" />
          <rect x="636" y="198" width="8" height="12" fill="#03301f" />
          <ellipse cx="912" cy="194" rx="13" ry="11" />
          <rect x="909" y="203" width="6" height="9" fill="#03301f" />
          <ellipse cx="1122" cy="190" rx="17" ry="14" />
          <rect x="1118" y="202" width="8" height="10" fill="#03301f" />
        </g>
        {/* Palm trees */}
        <g fill="none" stroke="#053a28" strokeWidth="4" strokeLinecap="round">
          <path d="M420,210 C422,196 414,184 400,174" />
          <path d="M1096,210 C1094,198 1100,186 1112,178" />
        </g>
        <g fill="#053a28">
          <path d="M400,174 C388,158 378,162 374,176 C388,176 396,172 400,174 Z" />
          <path d="M400,174 C408,156 420,160 424,174 C410,176 404,173 400,174 Z" />
          <path d="M400,174 C392,160 388,176 394,184 C398,180 400,176 400,174 Z" />
          <path d="M400,174 C410,162 414,176 408,184 C403,180 401,177 400,174 Z" />
          <path d="M1112,178 C1124,162 1136,166 1140,180 C1126,182 1118,179 1112,178 Z" />
          <path d="M1112,178 C1104,160 1092,164 1088,178 C1102,180 1108,177 1112,178 Z" />
          <path d="M1112,178 C1120,164 1116,180 1110,188 C1106,184 1104,180 1112,178 Z" />
          <path d="M1112,178 C1102,166 1098,180 1104,188 C1109,184 1111,181 1112,178 Z" />
        </g>
      </svg>

      <span className="scene-road" />
      <span className="scene-bus" role="presentation">
        <span className="bus-dust dust-1" />
        <span className="bus-dust dust-2" />
        <span className="bus-dust dust-3" />
        <svg
          viewBox="0 0 260 92"
          role="presentation"
          focusable="false"
          className="scene-bus-svg"
        >
          {/* Ground shadow */}
          <ellipse cx="130" cy="86" rx="104" ry="5" fill="#000" opacity="0.26" />
          {/* Body — deep/medium red */}
          <rect x="14" y="30" width="232" height="52" rx="11" fill="#b3261e" />
          {/* Roof cap (rounded, darker red) */}
          <path d="M28,30 L232,30 Q236,30 238,28 L238,24 Q238,21 235,21 L25,21 Q22,21 22,24 L22,28 Q24,30 28,30 Z" fill="#7a1510" />
          {/* Dark lower body (black/charcoal) */}
          <rect x="14" y="64" width="232" height="18" rx="9" fill="#15171a" />
          {/* Side windows — dark charcoal */}
          <g fill="#22262c">
            <rect x="34" y="42" width="36" height="16" rx="3" />
            <rect x="78" y="42" width="36" height="16" rx="3" />
            <rect x="122" y="42" width="36" height="16" rx="3" />
            <rect x="166" y="42" width="30" height="16" rx="3" />
          </g>
          {/* Door */}
          <rect x="202" y="46" width="10" height="34" rx="2" fill="#0d0f11" />
          <line x1="207" y1="48" x2="207" y2="78" stroke="#252a30" strokeWidth="2" />
          {/* Front windshield + nose (dark glass) */}
          <path d="M214,44 L224,34 L232,34 Q244,34 246,44 L246,60 L214,60 Z" fill="#1a1e24" />
          <path d="M217,44 L224,37 L234,37 L224,44 Z" fill="#2e333a" opacity="0.5" />
          {/* Headlight */}
          <circle cx="240" cy="70" r="4" fill="#f7efcf" />
          <circle cx="240" cy="70" r="1.8" fill="#ffffff" />
          {/* Front bumper */}
          <rect x="222" y="72" width="24" height="6" rx="3" fill="#2b2e33" />
          {/* Wheels */}
          <circle cx="66" cy="78" r="10" fill="#0b0b0c" />
          <circle cx="66" cy="78" r="4.5" fill="#6a6d72" />
          <circle cx="192" cy="78" r="10" fill="#0b0b0c" />
          <circle cx="192" cy="78" r="4.5" fill="#6a6d72" />
        </svg>
      </span>
    </div>
  )
})

export default HeaderScene