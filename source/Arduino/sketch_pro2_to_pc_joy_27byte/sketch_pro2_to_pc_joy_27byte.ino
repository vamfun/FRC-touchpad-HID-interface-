  #include <Joystick.h>

Joystick_ Joystick(JOYSTICK_DEFAULT_REPORT_ID,
                   JOYSTICK_TYPE_JOYSTICK,
                   32, 0,
                   true, true, true,
                   true, true, true,
                   true, true, true,
                   true, true);

// Config
const bool test_frame = false;
const bool printDebug = false;
const uint8_t FRAME_SIZE = 27;
const uint8_t SYNC_BYTE = 0X55;
uint8_t frame[FRAME_SIZE];
uint8_t frame_last[FRAME_SIZE];

const unsigned long keepAliveInterval = 200;
unsigned long lastSendTime = 0;

void setup() {
  Serial.begin(115200);
  if (!test_frame) Serial1.begin(115200);

  Joystick.setXAxisRange(0, 65535);
  Joystick.setYAxisRange(0, 65535);
  Joystick.setZAxisRange(0, 65535);
  Joystick.setRxAxisRange(0, 65535);
  Joystick.setRyAxisRange(0, 65535);
  Joystick.setRzAxisRange(0, 65535);
  Joystick.setRudderRange(0, 65535);
  Joystick.setThrottleRange(0, 65535);
  Joystick.setAcceleratorRange(0, 65535);
  Joystick.setBrakeRange(0, 65535);
  Joystick.setSteeringRange(0, 65535);

  Joystick.begin(false);
  delay(1000);
}

bool readFrame() {
  if (test_frame) {
    frame[0] = SYNC_BYTE;
    uint32_t btns = 0x1F;
    frame[1] = btns & 0xFF;
    frame[2] = (btns >> 8) & 0xFF;
    frame[3] = (btns >> 16) & 0xFF;
    frame[4] = (btns >> 24) & 0xFF;

    uint16_t values[11] = {
      1000, 2000, 3000,     // X, Y, Z
      4000, 5000, 6000,     // Rx, Ry, Rz
      7000, 8000, 9000,     // Rudder, Throttle, Accelerator
      10000, 11000          // Brake, Steering
    };
    for (uint8_t i = 0; i < 11; i++) {
      frame[5 + i * 2] = values[i] & 0xFF;
      frame[6 + i * 2] = (values[i] >> 8) & 0xFF;
    }
    return true;
  }

  while (Serial1.available() >= FRAME_SIZE) {
    if (Serial1.peek() == SYNC_BYTE) {
      Serial1.read();  // sync
      Serial1.readBytes(frame + 1, FRAME_SIZE - 1);
      frame[0] = SYNC_BYTE;
      return true;
    } else {
      Serial1.read();
    }
  }

  return false;
}

bool framesDiffer(const uint8_t* a, const uint8_t* b) {
  for (uint8_t i = 0; i < FRAME_SIZE; i++) {
    if (a[i] != b[i]) return true;
  }
  return false;
}

void applyFrame(bool printDebug) {
  uint32_t btn = frame[1] | (frame[2] << 8) | (frame[3] << 16) | (frame[4] << 24);
  uint16_t x = frame[5] | (frame[6] << 8);
  uint16_t y = frame[7] | (frame[8] << 8);
  uint16_t z = frame[9] | (frame[10] << 8);
  uint16_t rx = frame[11] | (frame[12] << 8);
  uint16_t ry = frame[13] | (frame[14] << 8);
  uint16_t rz = frame[15] | (frame[16] << 8);
  uint16_t rud = frame[17] | (frame[18] << 8);
  uint16_t thr = frame[19] | (frame[20] << 8);
  uint16_t acc = frame[21] | (frame[22] << 8);
  uint16_t brk = frame[23] | (frame[24] << 8);
  uint16_t str = frame[25] | (frame[26] << 8);

  
  if (printDebug) { /// print to pro2 terminal and skip joystick HID
    Serial.print("Btns: 0x"); Serial.print(btn, HEX);
    Serial.print(" X: "); Serial.print(x);
    Serial.print(" Y: "); Serial.print(y);
    Serial.print(" Z: "); Serial.print(z);
    Serial.print(" Rx: "); Serial.print(rx);
    Serial.print(" Ry: "); Serial.print(ry);
    Serial.print(" Rz: "); Serial.print(rz);
    Serial.print(" Rud: "); Serial.print(rud);
    Serial.print(" Thr: "); Serial.print(thr);
    Serial.print(" Acc: "); Serial.print(acc);
    Serial.print(" Brk: "); Serial.print(brk);
    Serial.print(" Str: "); Serial.println(str);
  }

  Joystick.setXAxis(x);
  Joystick.setYAxis(y);
  Joystick.setZAxis(z);
  Joystick.setRxAxis(rx);
  Joystick.setRyAxis(ry);
  Joystick.setRzAxis(rz);
  Joystick.setRudder(rud);
  Joystick.setThrottle(thr);
  Joystick.setAccelerator(acc);
  Joystick.setBrake(brk);
  Joystick.setSteering(str);

  for (uint8_t i = 0; i < 32; i++) {
    Joystick.setButton(i, (btn >> i) & 1);
  }

  noInterrupts();
  Joystick.sendState();
  interrupts();

  
}

void loop() {
  bool gotFrame = readFrame();
  bool changed = gotFrame && framesDiffer(frame, frame_last);
  bool timeout = (millis() - lastSendTime >= keepAliveInterval);

  if (changed || timeout) {
    applyFrame(changed);
    if (changed) memcpy(frame_last, frame, FRAME_SIZE);
    lastSendTime = millis();
  }
}