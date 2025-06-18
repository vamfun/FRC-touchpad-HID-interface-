// PRO1: Serial → Serial1 → PRO2 
// Make sure not to use "5v Joystick" processor when compiling ...that is reserved for pro2 
/*This sketch is written in support of a project to create a touchpad pc interface to an FRC driver station (DS).
One possible interface is with two Pro micro Arduino development boards, Pro1 and Pro2.  This sketch resides in Pro1.  
The sketch simply reads the  27 byte frame data from the touchpad GUI over USB native Serial port and then writes it directly to the
UART Serial1 port without any processing.  The Pro1 UART port is tied to Pro2 UART port where the data is read and
converted to an HID Joystick frame for use by the FRC drivers station.   
If using the Pro1/Pro2 approach , the boards must be given separte PID/VID hardware IDs if used in
 timingtest.java timing measurements.  I used default processor for Pro1 and renamed Pro2 to "5v Joystick"
  
Program was written by Chris Vamfun , team 599 mentor, as an example for interfacing touchpad generated inputs to DS rather than the 
usual all hardware interface.  

*/
void setup() {
  Serial.begin(115200);   // USB serial to PC
  Serial1.begin(115200);  // TX/RX to Pro2
  while (!Serial) ;       // Wait for Serial Monitor
  Serial.println("PRO1 Ready");
}

void loop() {
  // Read from PC and forward to Pro2
  while (Serial.available()) {
    char c = Serial.read();
    Serial1.write(c);  
   // Serial.write(c);       // Forward to Pro2

  }

 
  // Read echoed data from Pro2 and print
  while (Serial1.available()) {
    char c = Serial1.read();
    Serial.write("PRO2: ");
    Serial.println(c);
  }
}
