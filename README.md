# FRC-touchpad-HID-interface
This repository has Java and Arduino code to interface a windows based touchpad GUI with an FRC drive station using an HID device as a bridge.  
Included is an example of a touchpad prototype java swing gui that interfaces to an arduino pro micro or esp32-s3 development board.  A 27 byte test frame 
that includes  a sync byte , a 32 bit button word and 11 16 bit words of typical joystick data .. x,y,z,rx,ry,rz, plus 5 sim variables.   Also included is a windows based Java timing test program
that allows the user to measure frame timing with a single pc tha uses a comm port to send a frame and a usb frame reader that reads HID format data sent from device.  
Round trip timing can be estimated for varying BAUD rates.   See vamfun blog post https://vamfun.wordpress.com/2025/06/18/custom-hid-touchpad-interface-for-frc-driver-station/  for additional details.
