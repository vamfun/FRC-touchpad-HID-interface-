/*This sketch uses Heironomous Joystick_ESP32 to create an HID interface applicable to an FRC drivers station.
The ESP32-S3 is connected to a touch screen PC or tablet via USB com port.  GUI data is formatted into a 26 byte 
frame plus a leading sync byte.   The resulting 27 byte frame data is sent to a dual USB port ESP32-S3 which has a
 direct USB serial connection  to CH343P chip that bridges it to UART serial1 .   The serial1 read is run in core 0 to speed up timing.  The other functions default to core 1.byte
Program was written by Chris Vamfun , team 599 mentor, as an example for interfacing a touchpad inputs to DS rather than the 
usual all hardware interface.   The program has been tested at BAUD 115200 where it exhibits about 4ms round trip timming.
*/
#include <Joystick_ESP32S2.h>


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
const bool echo_frame = false;
bool starttimer1 = false;
bool gotFrame = false;
bool changed = false;


const uint8_t BUFFER_SIZE = 64;  // Must be > FRAME_SIZE
static uint8_t buffer[BUFFER_SIZE];
static size_t head = 0, tail = 0;
static unsigned long lastByteTime = 0;
const unsigned long FRAME_TIMEOUT = 100;  // ms

unsigned long  BAUD = 921600;
const uint8_t SYNC_BYTE = 0x55;
const uint8_t FRAME_SIZE = 27;
uint8_t frame[FRAME_SIZE];
uint8_t frame_last[FRAME_SIZE];

const unsigned long keepAliveInterval = 50;  //when DS used..set to 50ms
unsigned long lastSendTime = 0;
unsigned long startHidSendTime = 0;
unsigned long starttime1 = 0;
long delayus = 0;
bool readFrame();
void echoFrame();
void readFrameTask(void* parameter) {
  while (true) {
    if (!gotFrame) {
      gotFrame = readFrame();  // readFrame() parses Serial1 and updates `frame` if good
      echoFrame();
    }
    vTaskDelay(1);  // small delay to prevent hogging CPU
  }
}
void setup() {
  //Serial1.setPins(44,43,-1,-1);
  //Serial.begin(BAUD);
  //Serial1.end();
  //delay(1000);
  if (!test_frame) Serial1.begin(BAUD,SERIAL_8N1,44,43);  // CH343 USB-to-Serial port
  xTaskCreatePinnedToCore(
    readFrameTask,
    "ReadFrameTask",
    2048,
    NULL,
    1,
    NULL,
    0  // Core 0
  );
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
    frame[0] = 0x55;
    uint32_t btns = 0x1F;
    frame[1] = btns & 0xFF;
    frame[2] = (btns >> 8) & 0xFF;
    frame[3] = (btns >> 16) & 0xFF;
    frame[4] = (btns >> 24) & 0xFF;

    uint16_t values[11] = {
      1000, 2000, 3000,  // X, Y, Z
      4000, 5000, 6000,  // Rx, Ry, Rz
      7000, 8000, 9000,  // Rudder, Throttle, Accelerator
      10000, 11000       // Brake, Steering
    };
    for (uint8_t i = 0; i < 11; i++) {
      frame[5 + i * 2] = values[i] & 0xFF;
      frame[6 + i * 2] = (values[i] >> 8) & 0xFF;
    }
    return true;
  }


  // Read incoming bytes into ring buffer
  while (Serial1.available()) {
    buffer[head] = Serial1.read();
    head = (head + 1) % BUFFER_SIZE;
    lastByteTime = millis();

    // Prevent overrun
    if (head == tail) tail = (tail + 1) % BUFFER_SIZE;
  }

  // Search for sync byte in valid data window
  size_t bytesUsed = (head >= tail) ? (head - tail) : (BUFFER_SIZE - tail + head);
  for (size_t i = 0; i < bytesUsed; i++) {
    size_t idx = (tail + i) % BUFFER_SIZE;

    if (buffer[idx] == SYNC_BYTE) {
      // Check if full frame is available
      size_t remaining = (head >= idx) ? (head - idx) : (BUFFER_SIZE - idx + head);
      if (remaining >= FRAME_SIZE) {
        // Copy frame
        for (size_t j = 0; j < FRAME_SIZE; j++) {
          frame[j] = buffer[(idx + j) % BUFFER_SIZE];
        }
        // Advance tail to end of frame
        tail = (idx + FRAME_SIZE) % BUFFER_SIZE;
        return true;
      }
    }
  }

  // Timeout reset
  if (millis() - lastByteTime > FRAME_TIMEOUT) {
    tail = head;
  }

  return false;
}
void echoFrame() {
  if (echo_frame && changed) {
    Serial1.println("echo");
    Serial1.flush();
    frame[1] = 0xFF;
    delayMicroseconds(500);
    Serial1.write(frame, sizeof(frame));
  }
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


  if (printDebug) {  /// print to  terminal and skip joystick HID
    Serial1.print("Btns: 0x");
    Serial1.print(btn, HEX);
    Serial1.print(" X: ");
    Serial1.print(x);
    Serial1.print(" Y: ");
    Serial1.print(y);
    Serial1.print(" Z: ");
    Serial1.print(z);
    Serial1.print(" Rx: ");
    Serial1.print(rx);
    Serial1.print(" Ry: ");
    Serial1.print(ry);
    Serial1.print(" Rz: ");
    Serial1.print(rz);
    Serial1.print(" Rud: ");
    Serial1.print(rud);
    Serial1.print(" Thr: ");
    Serial1.print(thr);
    Serial1.print(" Acc: ");
    Serial1.print(acc);
    Serial1.print(" Brk: ");
    Serial1.print(brk);
    Serial1.print(" Str: ");
    Serial1.println(str);
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

  //noInterrupts(); //these worked with pro micro but had to be removed for ESP32
  Joystick.sendState();
  // interrupts();
}

void loop() {
  // startHidSendTime = micros();
  changed = gotFrame && framesDiffer(frame, frame_last);
  bool timeout = (millis() - lastSendTime >= keepAliveInterval);
  // bool timeout1 = starttimer1 && (micros() > starttime1 + 5000);
  // if (timeout1) {
  //   // String time_data = "<HIDCom:Joystick.send delay = " + String(delayus) + " us>";
  //   // Serial1.println(time_data);
  //   // Serial1.flush();
  //   gotFrame = false;
  //   starttimer1 = false;
  // }
  if (timeout || changed) {



    if (changed) {
      memcpy(frame_last, frame, FRAME_SIZE);
      //         if (!starttimer1) {
      //           starttime1 = micros();
      //           starttimer1 = true;
      //         }
      //         else
      //         {

      applyFrame(false);
      gotFrame = false;
    }

           lastSendTime = millis();
    //       delayus = micros() - startHidSendTime;}
    //       }
    // }
    // }
  }
}