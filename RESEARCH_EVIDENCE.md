# RASTA Universal Audio IR Remote — Research Evidence / Model Boundaries

## 1. Onkyo CR-185 / CR-185X — RC-332S

**Model-specific evidence**
- The CR-185X service material identifies the remote as RC-332S (part 24140332B / A801).
- The owner manual documents TONE, S.BASS, DIRECT, TIMER, UP/DOWN, ENTER, source selection, preset, CD controls and the physical RC-332S layout.
- The project keeps the previously tested 210/109 and 210/44 mappings as project mappings. They are **not** labelled as a complete factory IR table for CR-185X unless directly confirmed.
- User-confirmed records are the only records allowed to override the built-in mapping.

## 2. Sony CMT-E300HD — RM-E02D

**Model-specific evidence**
- Sony support/manual material identifies CMT-E300HD/E350HD with RM-E02D.
- The exact E300HD command/address table was not located in the public sources searched.
- Sony SIRC 12/15/20 is a known protocol family. It is not by itself proof of the E300HD command table.
- A public Sony AV SYSTEM table was found with 15-bit raw values such as:
  - VOL UP = 001812
  - VOL DOWN = 001813
  - MUTE = 001814
  - POWER = 001815
  These are stored in the UI as **research candidates**, not E300HD factory codes.
- The app includes a RAW SIRC sender so a raw code is transmitted exactly as a bit word instead of being incorrectly re-parsed into a guessed device/command pair.

## 3. Victor/JVC NX-TC5-B — RM-SNXTC5-S

**Model-specific evidence**
- The official JVC/Kenwood manual identifies RM-SNXTC5-S and documents the remote layout and functions including CD/iPod/USB/MD, 1Seg/FM-AM/AUDIO IN/PHOTO, navigation, timer, display, REC, AHB PRO, SOUND MODE and Manual EQ.
- The official JVC IR specification defines the generic JVC waveform as 940 nm, 37.9 kHz, 16 bits, pulse-interval modulation, 8.44 ms/4.22 ms header, 0.527 ms mark, 1.055 ms zero interval and 2.11 ms one interval, with a 46.42 ms word cycle.
- The exact custom/data values for NX-TC5 were not located in the public sources searched. The app therefore scans them as candidates.
- The encoder sends custom/address first and data/command second, LSB first, and targets the documented JVC word cycle.

## 4. Pioneer private SELFIE S5 / P700 — CU-AP015

**Model-specific evidence**
- CU-AP015 is identified with the P700/S5 component set: A-P700, GR-P700, F-P700, PD-P700, CT-700WR and S-P700.
- A reliable model-specific CU-AP015 command dump was not located in the public sources searched.
- Pioneer family research supports a 40 kHz Pioneer frame using D:8,S:8,F:8,~F:8; critically, Pioneer signals use D in the 160–175 range and have no independent subdevice field. The second byte is the complement of D. Some Pioneer commands use two 32-bit frames as a 64-bit message.
- The app uses this as a **family-level candidate protocol**, not a factory CU-AP015 command table.
- Discovery defaults to device 160–175 and command 0–255; the legacy memory field `subAddress` is retained only for schema compatibility and is ignored by the Pioneer encoder.

## 5. Verification policy

`SENT@frequency` means only that Android accepted/transmitted the IR pattern. The phone has no IR receiver in this architecture and therefore cannot automatically detect whether the stereo responded.

A record becomes active only when:
- `schema = RASTA-IR-MEMORY-4`
- `status = verified`
- `userConfirmed = true`

Imported legacy records are deliberately downgraded to candidate/unconfirmed so they cannot silently disable working mappings.

## 6. Primary web evidence used during the final audit

- Onkyo CR-185X manual / RC-332S: ManualsLib and Onkyo service material.
- Sony CMT-E300HD/E350HD RM-E02D: Sony support/manual material.
- Sony generic AV SYSTEM SIRC table: public technical research page documenting 15/20-bit examples.
- JVC NX-TC5 official manual: JVC/Kenwood manual PDF.
- JVC IR physical specification: JVC official `RemoteCodes.pdf`.
- Pioneer protocol: JP1 Pioneer IRP research and Tasmota Pioneer encoding documentation.
- Pioneer CU-AP015 / P700-S5 association: P700/S5 documentation and CU-AP015 listings.

These sources support protocol/family behavior and model/remote identity. They do **not** justify inventing a complete factory command table for the three models where no model-specific dump was found.
