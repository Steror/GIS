package app.part3;

import lombok.Getter;

import javax.swing.*;
import java.awt.*;

public class ConfigWindow extends JFrame {

    JFrame frame = new ConfigWindow();

    @Getter
    JTextField trackLength, trackWidth, distance1, distance2

    public ConfigWindow() {
        frame.setTitle("Configure operation");

        frame.getContentPane().setLayout(new FlowLayout());
        textfield1 = new JTextField("Text field 1",10);

        frame.setVisible(true);
    }
}
