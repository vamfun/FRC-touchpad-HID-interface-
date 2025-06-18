package timingtest;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import org.hid4java.*;
import org.hid4java.event.HidServicesEvent;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class timingtest {
    private static final int BAUDRATE= 921600;
    private static final int FRAME_SIZE = 27;
    private static final int VALUE_SIZE = (FRAME_SIZE-1)/2;
    //private static final int VENDOR_ID = 0x1B4F;    // SparkFun
    //private static final int PRODUCT_ID = 0x9208;   // 5V Joystick
    private static final int VENDOR_ID = 0x303A;    // 
    private static final int PRODUCT_ID = 0x1001;   // ES32
    private static HidDevice hidDevice = null;
    private static SerialPort serialPort = null;
    private static boolean enableHIDComPrint = false; 
    private static boolean echo_id_read = false;
    
        public static void main(String[] args)  throws InterruptedException  {
            // HID Device Setup
            HidServicesSpecification hidSpec = new HidServicesSpecification();
            hidSpec.setAutoShutdown(true);
            hidSpec.setScanMode(ScanMode.SCAN_AT_FIXED_INTERVAL);
    
            HidServices hidServices = HidManager.getHidServices(hidSpec);
    
            hidServices.addHidServicesListener(new HidServicesListener() {
                @Override
                public void hidDeviceAttached(HidServicesEvent event) {
                    System.out.println("HID device attached: " + event.getHidDevice());
                    checkAndSetDevice(event.getHidDevice());
                }
    
                @Override
                public void hidDeviceDetached(HidServicesEvent event) {
                    System.out.println("HID device detached: " + event.getHidDevice());
                    if (hidDevice == event.getHidDevice()) {
                        hidDevice = null;
                        System.out.println("Target device disconnected");
                    }
                }
    
                @Override
                public void hidFailure(HidServicesEvent event) {
                    System.err.println("HID failure: " + event);
                }
    
                @Override
                public void hidDataReceived(HidServicesEvent event) {
                   /*  if (event.getHidDevice() == hidDevice) {
                        byte[] data = new byte[FRAME_SIZE];
                        int bytesRead = event.getHidDevice().read(data);
                        if (bytesRead > 0) {
                            //System.out.println("HID data received:");
                           // printFrame(data);
                           // return data;
                        }
                    } */
                }
            });
    
           

            // Initial scan for devices
            System.out.println("Scanning for HID devices...");
            hidServices.scan();
    
            // Check for existing devices
            List<HidDevice> devices = hidServices.getAttachedHidDevices();
            for (HidDevice device : devices) {
                checkAndSetDevice(device);
            }
            
    
            if (hidDevice == null) {
                System.err.println("Target HID device not found. Will wait for connection...");
            } else 
            {  if(enableHIDComPrint) {  // Start a separate thread to listen for serial timing data
                new Thread(() -> {
                    //System.out.println("Started separate thread to listen to serial timing data");
    
                    //SerialPort serialPort = HIDMatchingComPort("5V Joystick");
                    serialPort =SerialPort.getCommPort("COM33");
                    if (serialPort != null) {
                    serialPort.setBaudRate(BAUDRATE);
                    serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);
                    serialPort.openPort();
                    if (serialPort.openPort()) {
                        System.out.println("Serial HID port opened successfully: " + serialPort.getSystemPortName());
                        startHIDComListener(serialPort);
                    }
                    
                 else 
                 {
                        System.err.println("Failed to open HID serial port.");
                    }
                }
                    else 
                    {System.err.println("HID COm Serial port is null ");}
                }).start();
            }
            }
    
    
            // Serial Communication Setup
            SerialPort[] ports = SerialPort.getCommPorts();
            if (ports.length == 0) {
                System.err.println("No serial ports available.");
                return;
            }
    
            SerialPort selectedPort = selectSerialPort(ports);
            if (selectedPort == null) {
                System.err.println("No suitable serial port found.");
                return;
            }
    
            System.out.println("Sending com port: " + selectedPort.getSystemPortName());
            try {
                Thread.sleep(500);  // 1 second delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while waiting for serial port readiness.");
            }
            // Configure serial port
            selectedPort.setBaudRate(BAUDRATE);
            selectedPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);
    
    
            if (!selectedPort.openPort()) {
                System.err.println("Failed to open send serial port.");
                cleanshutdown();
                return;
            }
    
            //enable echo of sent frame
            boolean echo_enable = false;
          
           
            selectedPort.addDataListener(new SerialPortDataListener() {
        public int getListeningEvents() { return SerialPort.LISTENING_EVENT_DATA_AVAILABLE; }
        public void serialEvent(SerialPortEvent event) {
            if(!echo_enable) return;
            if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) return;
    
            byte[] buffer = new byte[selectedPort.bytesAvailable()];
            int numRead = selectedPort.readBytes(buffer, buffer.length);
            // Print as string
            String debugText = new String(buffer, 0, numRead);
            if(echo_id_read) 
            { printFrame(buffer) ; // Display frame echo
                echo_id_read = false;
            }
            if(!echo_id_read ){
            if (debugText.contains("echo") || debugText.contains("HIDCom")) {
            System.out.println("ESP Frame Echo: ");
            System.out.println(debugText);
            echo_id_read = true;
            } 
            
             
            
            }
        }
        
    
           }
    );

  
    //Main communication loop **************************************************
    long sendPeriodMs = 500;
    long sendPeriodNs = sendPeriodMs * 1_000_000L;
    boolean print_frame= false;
    byte[] frame = createTestFrame();
    byte[] recv = new byte[FRAME_SIZE];
    int count = 0;

    long cum_rtt_ns = 0;
    
    long rtt_ns = 0;
    long Frame_time_ns = (long)((FRAME_SIZE * 10.0 / BAUDRATE) * 1e9);
    long timelastsend = System.nanoTime();
    System.out.println("Waiting for HID before sending...");
try {
    Thread.sleep(4000);
} catch (InterruptedException e) {
    e.printStackTrace();
}
System.out.println("Done waiting.");
    if(!print_frame) 
    { System.out.printf(" Frame no   RTT     twrite     RT delay \n"); }
    while (count < 40) {
        long now = System.nanoTime();
        if (now - timelastsend >= sendPeriodNs) {
            frame[1] = (byte) (count + 1);
            if(print_frame) {
            System.out.printf("\n--- Sent Frame %d ---%n", count + 1);
            printFrame(frame);}
    
            long tSent = System.nanoTime();
            selectedPort.writeBytes(frame, FRAME_SIZE);
            long twrite = System.nanoTime() - tSent;
    
            boolean matched = false;
            long tRecv = 0;
            long timeoutStart = System.nanoTime();
    
            recv = new byte[FRAME_SIZE];
            while (System.nanoTime() - timeoutStart < 200_000_000L) {  // 200ms
                if(hidDevice != null) {
                int bytesRead = hidDevice.read(recv);
                
                if (bytesRead == FRAME_SIZE && recv[1] == frame[1]) {
                    tRecv = System.nanoTime();
                    matched = true;
                    break;
                }
                // Do not break here — allow other reads
            }}
    
            if (matched) {
                if(print_frame) {
                System.out.printf("--- Received Frame %d ---%n", count + 1);
                printFrame(recv);
                }
                rtt_ns = tRecv - tSent;
                cum_rtt_ns =cum_rtt_ns + rtt_ns;
                long delay_ns = rtt_ns - Frame_time_ns;
    
                System.out.printf("   %d     %.2f ms    %.3f ms   %.2f ms\n",
                       count +1,  rtt_ns / 1e6, twrite / 1e6,  delay_ns/1e6);
                
            } else {
                System.err.println("Timeout waiting for matching HID report.");
                printFrame(recv);
            }
    
            timelastsend += sendPeriodNs;  // Keep consistent interval
            count++;
           
            //enableHIDComPrint = true;
            echo_id_read = false;
        }
          
    }
          double avgRTT = (double) (cum_rtt_ns/1e6)/(count);
          double avgDelay = avgRTT -(double) (Frame_time_ns/1e6);
          System.out.printf("avg RTT = %.2f ms, avg RT delay = %.2f ms , Frame time = %.3f \n", avgRTT,avgDelay,Frame_time_ns/1e6);
     cleanshutdown();
    }

    
    
    private static void cleanshutdown(){
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();      // Step 1: close the port
        }
        if(hidDevice!= null && hidDevice.open())
          { hidDevice.close();}
          System.exit(0);
    }
    private static void checkAndSetDevice(HidDevice device) {
        if (device.getVendorId() == VENDOR_ID && device.getProductId() == PRODUCT_ID) {
            hidDevice = device;
            System.out.println("Found target HID device: " + device.getProduct());
           boolean opened = false;
            int retries = 20;
            
            while (!opened && retries-- > 0) {
                try {
                    opened = device.open();
                    if (opened) {
                        System.out.println("✅ HID device opened successfully");
                    } else {
                        System.out.println("⏳ HID not ready yet, retrying...");
                        Thread.sleep(200);
                    }
                } catch (Exception e) {
                    System.err.println("❌ Exception opening HID: " + e.getMessage());
                    cleanshutdown();
                }
            }
            
            if (!opened) {
                System.err.println("‼️ Could not open HID device after retries");
                cleanshutdown();
            }
            
        }
    }

    private static SerialPort selectSerialPort(SerialPort[] ports) {
        System.out.println("Available serial ports:");
        for (SerialPort port : ports) {
            System.out.println(" - " + port.getSystemPortName() + " (" + port.getDescriptivePortName() + ")");
        }

        // Select first non-HID port
        for (SerialPort port : ports) {
            if (!port.getDescriptivePortName().toLowerCase().contains("hid")) {
                return port;
            }
        }

        return null;
    }

    private static SerialPort HIDMatchingComPort(String productDescription) {
        SerialPort[] ports = SerialPort.getCommPorts();
    
    
        for (SerialPort port : ports) {
            String desc = port.getPortDescription();
            String name = port.getSystemPortName();  // e.g., "COM23"
    
            if (desc.equalsIgnoreCase(productDescription)) {
                System.out.println("Matched: " + name + " (" + desc + ")");
                
                 
                
                    return port;
                }
        }
 
                System.out.println("No matching COM port found.");
                return null;
        
    }
    private static void startHIDComListener(SerialPort port) {
        if (port == null) {
            System.err.println("Cannot start listener: Serial port is null.");
            return;
        }
    
        System.out.println("Starting Pro2 listener on port: " + port.getSystemPortName());
        StringBuilder serialBuffer = new StringBuilder();
    
        port.addDataListener(new SerialPortDataListener() {
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
            }
    
            public void serialEvent(SerialPortEvent event) {
                byte[] buffer = new byte[port.bytesAvailable()];
                port.readBytes(buffer, buffer.length);
                serialBuffer.append(new String(buffer, StandardCharsets.UTF_8));
    
                long startTime = System.currentTimeMillis();
                String data = serialBuffer.toString();
                System.out.println(data);
    
                Pattern pattern = Pattern.compile("<HIDComm:(.*?)>");  // New delimiter: <HID:...>
                Matcher matcher = pattern.matcher(data);
    
                int lastMatchEnd = 0;
                boolean matched = false;
                while (!matched && (System.currentTimeMillis() - startTime) < 50) {
                    if (matcher.find()) {
                        String message = matcher.group(1);
                        System.out.println("Received HIDCom message: " + message);
                        enableHIDComPrint = false; 
                        lastMatchEnd = matcher.end();
                        matched = true;
                    } else {
                        try {
                            Thread.sleep(5); // Wait a bit and try again
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        data = serialBuffer.toString();
                        matcher = pattern.matcher(data);
                    }
                }
    
                if (!matched) {
                    System.err.println("Timeout: No complete HIDCom message received in 50 ms.");
                } else if (lastMatchEnd > 0) {
                    serialBuffer.delete(0, lastMatchEnd);
                }
            }
        });
    }
    


    private static byte[] createTestFrame() {
        byte[] frame = new byte[FRAME_SIZE];
        frame[0] = 0x55; // Sync byte

        // Buttons (4 bytes)
        int btns = 0x1F;
        frame[1] = (byte) (btns & 0xFF);
        frame[2] = (byte) ((btns >> 8) & 0xFF);
        frame[3] = (byte) ((btns >> 16) & 0xFF);
        frame[4] = (byte) ((btns >> 24) & 0xFF);

        // Analog values (11 x 2 bytes)
        int[] values = {1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000, 11000};
        for (int i = 0; i < 11; i++) {
            frame[5 + i * 2] = (byte) (values[i] & 0xFF);
            frame[6 + i * 2] = (byte) ((values[i] >> 8) & 0xFF);
        }

        return frame;
    }

    private static int[] decodeFrame(byte[] frame) {
        int[] values = new int[VALUE_SIZE]; 
        if (frame.length < FRAME_SIZE) {
            System.err.println("Invalid frame length: " + frame.length);
            return values;
        }

        values[0] = ((frame[4] & 0xFF) << 24) | ((frame[3] & 0xFF) << 16) |
                     ((frame[2] & 0xFF) << 8) | (frame[1] & 0xFF);

        
        for (int i = 0; i < 11; i++) {
            values[i] = ((frame[6 + i * 2] & 0xFF) << 8 | (frame[5 + i * 2] & 0xFF));
        }
        return values;
    }
       
    

    private static void printFrame(byte[] frame) {
        System.out.print("[ ");
        for (int i = 0; i < Math.min(frame.length, FRAME_SIZE); i++) {
            System.out.printf("%02X ", frame[i] & 0xFF);
        }
        System.out.println("]");
    }
}