package org.wsp.zen.test;

import org.wsp.zen.gif.impl.DefaultDecoder;
import org.wsp.zen.gif.util.BufferedImageDecoderBuilder;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.*;

/**
 * GIF 解码库功能与性能测试 GUI。
 * 支持文件路径 / 输入流两种模式，本地浏览或网络 URL 加载。
 * 已修复因解析异步完成前请求越界帧导致的播放卡死问题。
 */
public class GifTestViewer extends JFrame {

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private DefaultDecoder<BufferedImage> decoder;
    private final JLabel imageLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel frameInfoLabel = new JLabel("帧：0/0");
    private final JLabel timeCostLabel = new JLabel("耗时：0 ms");
    private JSlider frameSlider;
    private int currentFrame = 0;
    private volatile boolean playing = false;
    private long lastFrameTime = System.currentTimeMillis();
    private static final int MIN_DELAY = 30;
    private boolean updatingSlider = false;

    private final JRadioButton fileModeRadio = new JRadioButton("文件路径 (String)", true);
    private final JRadioButton streamModeRadio = new JRadioButton("输入流 (InputStream)");
    private final JTextField pathOrUrlField = new JTextField(35);
    private final JButton browseButton = new JButton("浏览...");
    private final JButton loadButton = new JButton("加载");
    private final JLabel modeHintLabel = new JLabel("当前：文件映射随机读取");

    private Thread playThread;

    public GifTestViewer() {
        setTitle("GIF解码库测试 - 数据源切换 & 帧耗时显示");
        setSize(960, 720);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                stopPlayingAndCleanup();
            }
        });

        decoder = buildDecoder();
        initControlPanel();
        initBottomPanel();
        add(new JScrollPane(imageLabel), BorderLayout.CENTER);
        setVisible(true);

        playThread = new Thread(this::playLoop, "GIF-Test-Play-Thread");
        playThread.setDaemon(false);
        playThread.start();
    }

    private void stopPlayingAndCleanup() {
        playing = false;
        if (playThread != null && playThread.isAlive()) {
            playThread.interrupt();
        }
        try {
            if (decoder != null) decoder.close();
        } catch (Exception ignored) {}
    }

    private DefaultDecoder<BufferedImage> buildDecoder() {
        return BufferedImageDecoderBuilder.newBuilder().build();
    }

    private void initControlPanel() {
        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(fileModeRadio);
        modeGroup.add(streamModeRadio);

        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        radioPanel.add(new JLabel("数据源模式："));
        radioPanel.add(fileModeRadio);
        radioPanel.add(streamModeRadio);
        radioPanel.add(modeHintLabel);

        fileModeRadio.addActionListener(e -> modeHintLabel.setText("当前：文件映射随机读取"));
        streamModeRadio.addActionListener(e -> modeHintLabel.setText("当前：数据一次性写入 FrameInfo"));

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        topPanel.add(radioPanel, gbc);

        gbc.gridy = 1;
        gbc.gridwidth = 1;
        topPanel.add(new JLabel("本地文件/URL："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        topPanel.add(pathOrUrlField, gbc);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        buttonRow.add(browseButton);
        buttonRow.add(loadButton);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        topPanel.add(buttonRow, gbc);
        add(topPanel, BorderLayout.NORTH);

        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("GIF图片", "gif"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                pathOrUrlField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        loadButton.addActionListener(e -> loadGif());
    }

    private void initBottomPanel() {
        frameSlider = new JSlider(JSlider.HORIZONTAL, 0, 0, 0);
        frameSlider.setMajorTickSpacing(10);
        frameSlider.setPaintTicks(true);
        frameSlider.addChangeListener(e -> {
            if (updatingSlider || frameSlider.getValueIsAdjusting()) return;
            int newFrame = frameSlider.getValue();
            if (decoder != null && newFrame >= 0 && newFrame < decoder.getFrameCount()) {
                currentFrame = newFrame;
                renderFrameWithTiming(currentFrame);
                updateFrameInfo();
                lastFrameTime = System.currentTimeMillis();
            }
        });

        JPanel buttonPanel = new JPanel();
        JButton prevBtn = new JButton("上一帧");
        JButton nextBtn = new JButton("下一帧");
        JButton playPauseBtn = new JButton("暂停");

        prevBtn.addActionListener(e -> manualChangeFrame(-1));
        nextBtn.addActionListener(e -> manualChangeFrame(1));
        playPauseBtn.addActionListener(e -> {
            if (decoder == null || decoder.getFrameCount() == 0) return;
            playing = !playing;
            playPauseBtn.setText(playing ? "暂停" : "播放");
            if (playing) lastFrameTime = System.currentTimeMillis();
        });

        buttonPanel.add(prevBtn);
        buttonPanel.add(nextBtn);
        buttonPanel.add(playPauseBtn);
        buttonPanel.add(frameInfoLabel);
        buttonPanel.add(timeCostLabel);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(frameSlider, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    // ---------- 加载 ----------
    private void loadGif() {
        String input = pathOrUrlField.getText().trim();
        if (input.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入本地文件路径或网络URL");
            return;
        }
        boolean isUrl = input.startsWith("http://") || input.startsWith("https://");
        boolean useFileMode = fileModeRadio.isSelected();

        loadButton.setEnabled(false);
        playing = false;

        // 先中断播放线程，避免 reset 期间线程阻塞在 getFrame 上
        if (playThread != null && playThread.isAlive()) {
            playThread.interrupt();
            try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }

        decoder.reset();
        currentFrame = 0;
        lastFrameTime = System.currentTimeMillis();
        SwingUtilities.invokeLater(() -> {
            imageLabel.setIcon(null);
            updateFrameInfo();
            timeCostLabel.setText("耗时：0 ms");
        });

        // 异步加载，load() 会阻塞直到至少 minFrames 帧解析完成（默认 1 帧）
        new Thread(() -> {
            try {
                if (useFileMode) {
                    if (isUrl) {
                        Path tempFile = Files.createTempFile("gif_viewer_", ".gif");
                        try (InputStream in = new URL(input).openStream()) {
                            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                        }
                        decoder.load(tempFile.toAbsolutePath().toString());
                    } else {
                        decoder.load(input);
                    }
                } else {
                    InputStream in = isUrl ? new URL(input).openStream() : Files.newInputStream(Path.of(input));
                    decoder.load(in);
                }

                // 加载返回（至少已有 1 帧），启动播放；播放循环会自动适应帧数增长
                SwingUtilities.invokeLater(() -> {
                    playing = true;
                    // 确保 currentFrame 不越界
                    if (currentFrame >= decoder.getFrameCount()) currentFrame = 0;
                    renderFrameWithTiming(currentFrame);
                    updateFrameInfo();
                    loadButton.setEnabled(true);
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "加载失败：" + ex.getMessage());
                    loadButton.setEnabled(true);
                });
            }
        }).start();
    }

    // ---------- 帧操作 ----------
    private void manualChangeFrame(int offset) {
        if (decoder == null || decoder.getFrameCount() == 0) return;
        int total = decoder.getFrameCount();
        currentFrame = (currentFrame + offset + total) % total;
        renderFrameWithTiming(currentFrame);
        updateFrameInfo();
        lastFrameTime = System.currentTimeMillis();
    }

    /**
     * 渲染指定帧，含超时保护（5秒）。遇到异常不改变播放状态，由播放循环自行重试。
     */
    private void renderFrameWithTiming(int index) {
        if (decoder == null) return;
        long start = System.nanoTime();
        CompletableFuture<BufferedImage> future = decoder.getFrame(index);
        try {
            BufferedImage img = future.get(5, TimeUnit.SECONDS);
            long costNs = System.nanoTime() - start;
            double costMs = costNs / 1_000_000.0;
            SwingUtilities.invokeLater(() -> {
                if (img != null) imageLabel.setIcon(new ImageIcon(img));
                timeCostLabel.setText(String.format("耗时：%.2f ms", costMs));
            });
        } catch (Exception e) {
            // 超时、中断或执行异常均不改变 playing 状态，播放循环将继续重试下一帧
            // 仅刷新耗时标签提示异常
            SwingUtilities.invokeLater(() -> timeCostLabel.setText("帧获取异常"));
        }
    }

    private void updateFrameInfo() {
        SwingUtilities.invokeLater(() -> {
            int total = (decoder != null) ? decoder.getFrameCount() - 1 : 0;
            frameInfoLabel.setText("帧：" + currentFrame + "/" + total);

            if (frameSlider != null) {
                updatingSlider = true;
                if (decoder != null && decoder.getFrameCount() > 0) {
                    int max = decoder.getFrameCount() - 1;
                    if (frameSlider.getMaximum() != max) frameSlider.setMaximum(max);
                    frameSlider.setValue(currentFrame);
                } else {
                    frameSlider.setMaximum(0);
                    frameSlider.setValue(0);
                }
                updatingSlider = false;
            }
        });
    }

    /**
     * 播放循环：根据帧延迟自动切换帧。
     * 关键修复：请求帧前做边界检查，避免因异步解析导致越界；异常时不停止播放。
     */
    private void playLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (!playing || decoder == null || decoder.getFrameCount() == 0) {
                    Thread.sleep(10);
                    continue;
                }

                int totalFrames = decoder.getFrameCount();
                // 边界保护：如果当前帧索引超出已解析范围，回到第 0 帧
                if (currentFrame >= totalFrames) {
                    currentFrame = 0;
                }

                int delay = Math.max(decoder.getDelayTime(currentFrame), MIN_DELAY);
                long now = System.currentTimeMillis();
                if (now - lastFrameTime >= delay) {
                    int nextFrame = (currentFrame + 1) % totalFrames;
                    currentFrame = nextFrame;
                    renderFrameWithTiming(currentFrame);
                    updateFrameInfo();
                    lastFrameTime = now;
                }
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // 加载过程中的中断，清除标志后暂停，但不退出循环
                Thread.interrupted();
                playing = false;
                try { Thread.sleep(10); } catch (InterruptedException ignored) { Thread.interrupted(); }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GifTestViewer::new);
    }
}