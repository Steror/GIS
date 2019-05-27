package app.part3;

import lombok.Getter;

import javax.swing.*;

public class ConfigWindow extends JFrame {

    @Getter
    JFrame frame;

    @Getter
    JTextField trackLength, trackWidth, distance1, distance2, averageSlope;

    public ConfigWindow() {
        frame = new JFrame();
        frame.setTitle("Configure operation");

        frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.PAGE_AXIS));
        JLabel lengthLabel = new JLabel("L1: Track Length");
        trackLength = new JTextField("");
        JLabel widthLabel = new JLabel("L2: Track Width");
        trackWidth = new JTextField("");
        JLabel distance1Label = new JLabel("D1: Distance from roads and rivers");
        distance1 = new JTextField("");
        JLabel distance2Label = new JLabel("D2: Distance from bodies of water, community gardens and urbanised areas");
        distance2 = new JTextField("");
        JLabel slopeLabel = new JLabel("A1: Average track slope");
        averageSlope = new JTextField("");
        frame.getContentPane().add(lengthLabel);
        frame.getContentPane().add(trackLength);
        frame.getContentPane().add(widthLabel);
        frame.getContentPane().add(trackWidth);
        frame.getContentPane().add(distance1Label);
        frame.getContentPane().add(distance1);
        frame.getContentPane().add(distance2Label);
        frame.getContentPane().add(distance2);
        frame.getContentPane().add(slopeLabel);
        frame.getContentPane().add(averageSlope);

        JButton okButton = new JButton("OK");
        okButton.addActionListener(
                e -> frame.setVisible(false));
        frame.getContentPane().add(okButton);

        frame.pack();
        frame.setVisible(true);
    }
}
