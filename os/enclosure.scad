// ── EduPulse Kiosk Enclosure ──
// Raspberry Pi 4/5 + 7" Touchscreen + Camera mount
// Print on 220x220mm bed in 2 parts (front/back)

$fn = 64;

// ── Dimensions ──
SCREEN_W = 194;  // 7" display visible area
SCREEN_H = 110;
SCREEN_R = 4;    // corner radius

BEZEL = 12;      // border around screen
DEPTH = 55;      // internal depth
WALL = 2.5;

CAM_D = 12;      // camera hole diameter
CAM_X = 80;      // camera offset from center

VENT_W = 3;
VENT_GAP = 4;

// ── Front Panel ──
module front() {
  W = SCREEN_W + BEZEL * 2;
  H = SCREEN_H + BEZEL * 2 + 30;  // extra chin for branding

  difference() {
    // Main body
    rounded_rect(W, H, DEPTH, SCREEN_R + 2);

    // Screen cutout
    translate([BEZEL, BEZEL + 15, WALL])
      rounded_rect(SCREEN_W, SCREEN_H, DEPTH, SCREEN_R);

    // Camera hole (top center-right)
    translate([W/2 + CAM_X, BEZEL - 2, -1])
      cylinder(d = CAM_D, h = WALL + 2);

    // Speaker grille (bottom-left)
    for (i = [0:5]) {
      translate([15 + i * (VENT_W + VENT_GAP), H - 25, -1])
        cube([VENT_W, 20, WALL + 2]);
    }

    // LED indicator hole
    translate([W - 20, BEZEL + 5, -1])
      cylinder(d = 3, h = WALL + 2);

    // Screw bosses (countersink)
    for (x = [8, W - 8], y = [8, H - 8])
      translate([x, y, -1])
        cylinder(d1 = 6, d2 = 3.5, h = WALL + 2);
  }

  // Screen bezel lip
  %translate([BEZEL - 1, BEZEL + 14, WALL])
    linear_extrude(2)
      offset(r = -1)
        rounded_square(SCREEN_W + 2, SCREEN_H + 2, SCREEN_R);
}

// ── Back Panel ──
module back() {
  W = SCREEN_W + BEZEL * 2;
  H = SCREEN_H + BEZEL * 2 + 30;

  difference() {
    union() {
      // Main body
      rounded_rect(W, H, DEPTH, SCREEN_R + 2);

      // Pi standoffs
      pi_standoffs();
    }

    // Interior hollow
    translate([WALL, WALL + 15, WALL])
      rounded_rect(W - WALL*2, H - WALL*2 - 15, DEPTH, SCREEN_R);

    // Ventilation slots (top)
    for (i = [0:14]) {
      translate([15 + i * (VENT_W + VENT_GAP), H - 8, DEPTH/2])
        cube([VENT_W, 12, WALL + 2]);
    }

    // Power button hole
    translate([W/2, 5, DEPTH/2])
      cylinder(d = 8, h = WALL + 2);

    // USB/HDMI cutout
    translate([W - 30, 10, DEPTH/2 - 10])
      cube([20, 30, WALL + 2]);

    // Screw holes
    for (x = [8, W - 8], y = [8, H - 8])
      translate([x, y, -1])
        cylinder(d = 2.5, h = DEPTH + 2);
  }
}

// ── Helpers ──
module rounded_rect(w, h, d, r) {
  linear_extrude(d)
    rounded_square(w, h, r);
}

module rounded_square(w, h, r) {
  offset(r = r)
    square([w - r*2, h - r*2], center = true);
}

module pi_standoffs() {
  // Pi 4/5 mounting holes (centered on board)
  pi_w = 85;
  pi_h = 56;
  off_x = 25;
  off_y = 20;

  for (x = [off_x, off_x + pi_w], y = [off_y, off_y + pi_h]) {
    translate([x, y, DEPTH - 8])
      difference() {
        cylinder(d = 6, h = 8);
        translate([0, 0, -1])
          cylinder(d = 2.5, h = 10);
      }
  }
}

// ── Render ──
// Uncomment to render individual parts
// front();
translate([0, 200, 0]) back();
