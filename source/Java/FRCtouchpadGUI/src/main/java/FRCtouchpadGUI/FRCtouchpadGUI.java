package FRCtouchpadGUI;
/* Program written by Chris Vamfun Team 599 mentor vamfun@yahoo.com
 * This program uses Javaswing to create a reef scape example GUI 
 * where operator can select any reef and level and transmit to serial 
 * output that is read and processed by HID software in a Arduino or ESP32 
 * device.   The selected scoring target is packaged as a button word in a 27 byte Joystick 
 * frame along with two data words  : max speed and closing speed sent as x, y.
 * Upon first selection reef/level turn amber.  When send button is hit, the
 * selection is sent to the DS and the selections turn green.  The selections
 * can be reset by hitting the RED reset button.  
 * When program is first opened , the com port must be initialized using the 
 * dropdown port option box.  After comm port selection then hit RUN button. 
 * Frame data is echoed in the terminal display area to confirm selection.
 */

import com.fazecast.jSerialComm.SerialPort;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

public class FRCtouchpadGUI {
    private static SerialPort serialPort;
    private static SerialPort currentPort = null;  
    private static JComboBox<String> portList;
    private static JButton initButton, runButton, sendButton, resetButton;
    private static JTextArea terminalArea;
    // Declare scrollPane as an instance variable
    private  static JScrollPane scrollPane;

    //private static Timer sendTimer;
    private static ArrayList<JToggleButton> reefButtons = new ArrayList<>();
    private static JToggleButton[] levelButtons = new JToggleButton[4];
    private static JSlider sliderMaxSpeed, sliderApproachSpeed;
    private static JLabel maxSpeedValue, approachSpeedValue;

    final private static int FRAME_SIZE = 27;
    private static byte[] frame_last= new byte[FRAME_SIZE];
    public static void main(String[] args) {
        SwingUtilities.invokeLater(FRCtouchpadGUI::createAndShowGUI);
        System.out.println("Program started");
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("FRC Reef GUI Interface");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // === Top Panel ===
        JPanel portPanel = new JPanel();
        portList = new JComboBox<>();
        initButton = new JButton("Init Port");
        runButton = new JButton("Run");
        runButton.setEnabled(false);

        initButton.addActionListener(e -> initPorts());
        runButton.addActionListener(e -> startListening());

        portPanel.add(portList);
        portPanel.add(initButton);
        portPanel.add(runButton);
        frame.add(portPanel, BorderLayout.NORTH);

        // === Center Panel ===
        JPanel centerPanel = new JPanel(new BorderLayout());
        JPanel reefPanel = createReefPanel();
        JPanel sliderPanel = createSliderPanel();

        JPanel controlPanel = new JPanel();
        //sendButton = new JButton("Send");
        //resetButton = new JButton("Reset");

        sendButton.addActionListener(e -> {
            updateButtonColorsAfterSend();
            
            sendFrame();
             
        });

        resetButton.addActionListener(e -> resetReefButtons());

        

        centerPanel.add(reefPanel, BorderLayout.CENTER);
        centerPanel.add(sliderPanel, BorderLayout.EAST);
        centerPanel.add(controlPanel, BorderLayout.SOUTH);

        frame.add(centerPanel, BorderLayout.CENTER);

// Inside your constructor or initialization method
terminalArea = new JTextArea(8, 50);
terminalArea.setEditable(false);
scrollPane = new JScrollPane(terminalArea);
frame.add(scrollPane, BorderLayout.SOUTH);

frame.pack();
frame.setVisible(true);
    }




    private static JPanel createReefPanel() {
        JPanel reefPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(Color.LIGHT_GRAY);
                int centerX = getWidth() / 2;
                int centerY = getHeight() / 2;
                int radius = 150;
                Polygon hex = new Polygon();
                for (int i = 0; i < 6; i++) {
                    double angle = Math.toRadians(60 * i - 60); // Top is flat
                    int x = centerX + (int) (radius * Math.cos(angle));
                    int y = centerY + (int) (radius * Math.sin(angle));
                    hex.addPoint(x, y);
                }
                g2.drawPolygon(hex);
            }
        };
        reefPanel.setLayout(null);
        reefPanel.setPreferredSize(new Dimension(400, 400));
    
        int centerX = 200;
        int centerY = 240;
        int buttonRadius = 21;
        int ringRadius = 170;
        int side = -1; // left side equals -1   
        String label = " ";
        
        // Creating Reef Buttons
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30 - 105);
            int x = centerX + (int) (ringRadius * Math.cos(angle)) - buttonRadius;
            int y = centerY + (int) (ringRadius * Math.sin(angle)) - buttonRadius;
            if (side < 0) {
                side = -side;
                label = ((i / 2 + 1) + "L");
            } else {
                side = -side;
                label = ((i / 2 + 1) + "R");
            }
    
            JToggleButton btn = new JToggleButton(label);
            btn.setBounds(x, y, 2 * buttonRadius, 2 * buttonRadius);
            btn.setBackground(Color.LIGHT_GRAY);
            btn.setFocusPainted(false);
            btn.setOpaque(true);
            btn.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            btn.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI()); // Essential UI update
            btn.addActionListener(e -> {
                for (JToggleButton b : reefButtons) b.setSelected(false);
                btn.setSelected(true);
                updateButtonColors();
            });
            reefPanel.add(btn);
            reefButtons.add(btn);
        }
    
        // Adding Level Buttons
        buttonRadius = 25;
        int lx = centerX - buttonRadius;
        int ly = centerY - 2 * buttonRadius - 55;
        for (int i = 0; i < 4; i++) {
            JToggleButton lvl = new JToggleButton("L" + (4 - i));
            lvl.setBounds(lx, ly + i * (2 * buttonRadius + 5), 2 * buttonRadius, 2 * buttonRadius);
            lvl.setBackground(Color.LIGHT_GRAY); // Start with grey (reset state)
            lvl.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI()); // Essential UI update
            
            // Add action listener to manage button selection and color
            lvl.addActionListener(e -> {
                for (JToggleButton b : levelButtons) b.setSelected(false); // Deselect all level buttons
                lvl.setSelected(true); // Select this button
                updateButtonColors(); // Update button colors after selection
            });
    
            reefPanel.add(lvl);
            levelButtons[i] = lvl;
        }
    
        // Creating Send and Reset Buttons inside the reef area (centered)
        int sendButtonX = centerX - 120; // Place Send button to the left side inside the hexagon
        int resetButtonX = centerX + 40; // Place Reset button to the right side inside the hexagon
        int buttonY = centerY - 10; // Centered vertically within the reef
    
        sendButton = new JButton("Send");
        sendButton.setBounds(sendButtonX, buttonY, 80, 30);
        sendButton.setBackground(Color.GREEN);
        sendButton.setOpaque(true);
        sendButton.setBorder(BorderFactory.createLineBorder(Color.BLACK));
    
        resetButton = new JButton("Reset");
        resetButton.setBounds(resetButtonX, buttonY, 80, 30);
        resetButton.setBackground(Color.RED);
        resetButton.setOpaque(true);
        resetButton.setBorder(BorderFactory.createLineBorder(Color.BLACK));
    
        // Add the buttons to the panel
        reefPanel.add(sendButton);
        reefPanel.add(resetButton);
        
        return reefPanel;
    }
    
    public static void appendText(String text) {
        terminalArea.append(text );
    
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }
    

    private static JPanel createSliderPanel() {
        JPanel sliderPanel = new JPanel();
        sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.Y_AXIS));

        JLabel maxSpeedLabel = new JLabel("Max Speed m/s*100");
        sliderMaxSpeed = new JSlider(JSlider.VERTICAL, 300, 500, 400);
        maxSpeedValue = new JLabel("400");
        sliderMaxSpeed.addChangeListener(e -> maxSpeedValue.setText("" + sliderMaxSpeed.getValue()));

        JLabel approachSpeedLabel = new JLabel("Approach Speed m/s*100");
        sliderApproachSpeed = new JSlider(JSlider.VERTICAL, 100, 300, 200);
        approachSpeedValue = new JLabel("200");
        sliderApproachSpeed.addChangeListener(e -> approachSpeedValue.setText("" + sliderApproachSpeed.getValue()));

        sliderPanel.add(maxSpeedLabel);
        sliderPanel.add(sliderMaxSpeed);
        sliderPanel.add(maxSpeedValue);
        sliderPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        sliderPanel.add(approachSpeedLabel);
        sliderPanel.add(sliderApproachSpeed);
        sliderPanel.add(approachSpeedValue);

        return sliderPanel;
    }

    private static void updateButtonColors() {
       // Loop through all reef buttons
    for (JToggleButton btn : reefButtons) {
        if (btn.isSelected()) {
            // Set the selected button to red
            btn.setBackground(Color.ORANGE);
        } else {
            // Reset all other buttons to grey
            btn.setBackground(Color.LIGHT_GRAY);
        }
    }

    // Loop through all level buttons
    for (JToggleButton lvl : levelButtons) {
        if (lvl.isSelected()) {
            // Set the selected level button to red
            lvl.setBackground(Color.ORANGE);
        } else {
            // Reset all other level buttons to grey
            lvl.setBackground(Color.LIGHT_GRAY);
        }
    }
}

    
    
    
    

    private static void updateButtonColorsAfterSend() {
       // Loop through all reef buttons
    for (JToggleButton btn : reefButtons) {
        if (btn.isSelected()) {
            // Turn selected button to green when sent
            btn.setBackground(Color.GREEN);
        } 
        // If not selected, do nothing or leave them grey
    }

    // Loop through all level buttons
    for (JToggleButton lvl : levelButtons) {
        if (lvl.isSelected()) {
            // Turn selected level button to green when sent
            lvl.setBackground(Color.GREEN);
        }
        // If not selected, do nothing or leave them grey
    }
         
       // sendFrame();
         
    }

    private static void resetReefButtons() {
        for (JToggleButton btn : reefButtons) {
            btn.setSelected(false);
            btn.setBackground(Color.LIGHT_GRAY);
        }
        for (JToggleButton lvl : levelButtons) {
            lvl.setSelected(false);
            lvl.setBackground(Color.LIGHT_GRAY);
        }
        sendFrame();
    }

    
 

    private static void initPorts() {
        // Close any open port before rescanning
        if (serialPort != null && serialPort.isOpen()) {
            appendText("Closing previous port: " + serialPort.getSystemPortName() + "\n");
            serialPort.closePort();
        }
    
        // Rescan available ports and populate the port list
        portList.removeAllItems();  // Clear previous entries
        for (SerialPort port : SerialPort.getCommPorts()) {
            portList.addItem(port.getSystemPortName());
        }
    
        appendText("Ports refreshed. Select a port and press Run.\n");
    
        // Enable the Run button now that ports have been refreshed
        runButton.setEnabled(true);
    }
    
    private static void startListening() {
        String selected = (String) portList.getSelectedItem();
        if (selected == null) {
            appendText("No port selected.\n");
            return;
        }
    
        String portName = selected.split(" ")[0];  // Extract port name
    
        // If port is already open, show message that it's already running
        if (serialPort != null && serialPort.isOpen()) {
            appendText("Port is already running.\n");
            return;
        }
    
        // Open the selected port
        serialPort = SerialPort.getCommPort(portName);
        serialPort.setBaudRate(115200);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
        serialPort.setParity(SerialPort.NO_PARITY);
    
        if (!serialPort.openPort()) {
            appendText("Failed to open " + portName + "\n");
            return;
        }
    
        appendText("Port opened: " + portName + "\n");
    
        // Start listening in a separate thread
        new Thread(() -> {
            byte[] buffer = new byte[1];
            while (serialPort.isOpen()) {
                while (serialPort.bytesAvailable() > 0) {
                    serialPort.readBytes(buffer, 1);
                    appendText(new String(buffer));  // Print each byte received
                }
    
                try {
                    Thread.sleep(1);  // Small delay to avoid high CPU usage
                } catch (InterruptedException ignored) {}
            }
        }).start();
    }
    

     
    private static void sendFrame() {
        int buttonWord = 0;
        short x = (short) (50*sliderMaxSpeed.getValue());
        short y = (short) (50*sliderApproachSpeed.getValue());
        short z =  10000;
        short rx = 20000;
        short ry = 30000;
        short rz = 400;
        short rud = 500;
        short thr = 6000;
        short acc = 700;
        short brk = 800;
        short str = 9000;
        
       
        
        for (int i = 0; i < reefButtons.size(); i++) {
            if (reefButtons.get(i).isSelected()) {
                buttonWord |= (1 << i);
            }
        }
        for (int i = 0; i < 4; i++) {
            if (levelButtons[i].isSelected()) {
                buttonWord |= (1 << (12 + i));
            }
        }
        
    
        // --- FRAME_SIZE-byte frame ---
        byte sync = (byte) 0x55;
        ByteBuffer buffer = ByteBuffer.allocate(FRAME_SIZE);
        buffer.order(ByteOrder.LITTLE_ENDIAN); // Match Arduino
        buffer.put(sync);
        buffer.putInt(buttonWord);
        buffer.putShort(x);
        buffer.putShort(y);
        buffer.putShort(z);
        buffer.putShort(rx);
        buffer.putShort(ry);
        buffer.putShort(rz);
        buffer.putShort(rud);
        buffer.putShort(thr);
        buffer.putShort(acc);
        buffer.putShort(brk);
        buffer.putShort(str);



        byte[] frame = buffer.array();

        if( Arrays.equals(frame,frame_last)) return;//only send if change 

        frame_last = Arrays.copyOf(frame, frame.length);

    
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.writeBytes(frame, frame.length); 
            String output = String.format(
                "Sent frame: Btns=%16s X=%d Y=%d Z=%d\n",
                String.format("%16s", Integer.toBinaryString(buttonWord)).replace(' ', '0'),
                x, y, z
            );
            appendText(output);
        }
    }
    
}


