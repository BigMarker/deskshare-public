package com.bigmarker.client.deskshare;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/**
 * Provides a means to reduce the amount of the screen being captured.
 * 
 * @author Paul Gregoire
 */
public class SnipIt {

	private UIView view;
		
    public SnipIt(UIView view) {
    	this.view = view;
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ex) {
                }
                JFrame frame = new JFrame();
                frame.setUndecorated(true);
                // This works differently under Java 6
                frame.setBackground(new Color(0, 0, 0, 0));
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setLayout(new BorderLayout());
                frame.add(new SnipItPane());
                frame.setBounds(getVirtualBounds());
                frame.setVisible(true);
            }
        });
    }

    @SuppressWarnings("serial")
	public class SnipItPane extends JPanel {

        private Point mouseAnchor;
        private Point dragPoint;

        private SelectionPane selectionPane;

        public SnipItPane() {
            setOpaque(false);
            setLayout(null);
            selectionPane = new SelectionPane();
            add(selectionPane);
            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    mouseAnchor = e.getPoint();
                    dragPoint = null;
                    selectionPane.setLocation(mouseAnchor);
                    selectionPane.setSize(0, 0);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    dragPoint = e.getPoint();
                    int width = dragPoint.x - mouseAnchor.x;
                    int height = dragPoint.y - mouseAnchor.y;

                    int x = mouseAnchor.x;
                    int y = mouseAnchor.y;

                    if (width < 0) {
                        x = dragPoint.x;
                        width *= -1;
                    }
                    if (height < 0) {
                        y = dragPoint.y;
                        height *= -1;
                    }
                    selectionPane.setBounds(x, y, width, height);
                    selectionPane.revalidate();
                    repaint();
                }
            };
            addMouseListener(adapter);
            addMouseMotionListener(adapter);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2d = (Graphics2D) g.create();

            Rectangle bounds = new Rectangle(0, 0, getWidth(), getHeight());
            Area area = new Area(bounds);
            area.subtract(new Area(selectionPane.getBounds()));

            g2d.setColor(new Color(192, 192, 192, 64));
            g2d.fill(area);

        }
    }

    @SuppressWarnings("serial")
	public class SelectionPane extends JPanel {

        private JButton button;
        
        private JButton cancel;

        private JLabel label;

        public SelectionPane() {
            button = new JButton("Ok");
            cancel = new JButton("Cancel");
            setOpaque(false);

            label = new JLabel("Rectangle");
            label.setOpaque(true);
            label.setBorder(new EmptyBorder(4, 4, 4, 4));
            label.setBackground(Color.GRAY);
            label.setForeground(Color.WHITE);
            setLayout(new GridBagLayout());

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            add(label, gbc);

            gbc.gridy++;
            add(button, gbc);

            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                	view.updateBounds(getX(), getY(), getWidth(), getHeight());
                    SwingUtilities.getWindowAncestor(SelectionPane.this).dispose();
                }
            });
            
            cancel.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    SwingUtilities.getWindowAncestor(SelectionPane.this).dispose();
                }
            });
            
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    label.setText("Rectangle " + getX() + "x" + getY() + "x" + getWidth() + "x" + getHeight());
                }
            });

        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            float dash1[] = {10.0f};
            BasicStroke dashed =
                    new BasicStroke(3.0f,
                    BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER,
                    10.0f, dash1, 0.0f);
            g2d.setColor(Color.BLACK);
            g2d.setStroke(dashed);
            g2d.drawRect(0, 0, getWidth() - 3, getHeight() - 3);
            g2d.dispose();
        }
    }

    public static Rectangle getVirtualBounds() {
        Rectangle bounds = new Rectangle(0, 0, 0, 0);
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice lstGDs[] = ge.getScreenDevices();
        for (GraphicsDevice gd : lstGDs) {
            bounds.add(gd.getDefaultConfiguration().getBounds());
        }
        return bounds;
    }
    
}
