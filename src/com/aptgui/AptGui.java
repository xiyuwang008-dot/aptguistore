package com.aptgui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * APT 图形化软件管理器主界面。
 *
 * 功能：
 *   1. 搜索框搜索 APT 源中的软件包
 *   2. 点击结果查看软件包详情
 *   3. 一键安装（通过 pkexec 提权）
 *   4. 实时显示安装日志
 */
public class AptGui extends JFrame {

    private static final long serialVersionUID = 1L;

    private final AptManager aptManager = new AptManager();

    // UI 组件
    private JTextField searchField;
    private JButton searchButton;
    private JButton updateButton;
    private JList<PackageInfo> resultList;
    private DefaultListModel<PackageInfo> listModel;
    private JTextArea detailArea;
    private JTextArea logArea;
    private JButton installButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;

    // 当前选中的包
    private PackageInfo selectedPackage;

    public AptGui() {
        super("APT 软件管理器");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 650);
        setMinimumSize(new Dimension(700, 500));
        setLocationRelativeTo(null);

        initUI();
        setVisible(true);
    }

    private void initUI() {
        // 顶部搜索栏
        JPanel topPanel = new JPanel(new BorderLayout(8, 0));
        topPanel.setBorder(new EmptyBorder(10, 10, 8, 10));

        searchField = new JTextField();
        searchField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        searchField.putClientProperty("JTextField.placeholderText", "输入软件包名称搜索，例如：firefox、vim、chrome...");
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    doSearch();
                }
            }
        });

        searchButton = new JButton("搜索");
        searchButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        searchButton.addActionListener(e -> doSearch());

        updateButton = new JButton("刷新源");
        updateButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        updateButton.addActionListener(e -> doUpdate());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttonPanel.add(searchButton);
        buttonPanel.add(updateButton);

        topPanel.add(searchField, BorderLayout.CENTER);
        topPanel.add(buttonPanel, BorderLayout.EAST);

        // 左侧：搜索结果列表
        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    showPackageDetail();
                }
            }
        });
        resultList.addListSelectionListener(e -> showPackageDetail());

        JScrollPane listScroll = new JScrollPane(resultList);
        listScroll.setBorder(new TitledBorder("搜索结果"));

        // 右侧：详情 + 日志
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        detailArea = new JTextArea();
        detailArea.setEditable(false);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        detailArea.setText("在左侧选择一个软件包以查看详情。");
        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(new TitledBorder("软件包详情"));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(200, 230, 200));
        logArea.setCaretColor(Color.WHITE);
        logArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("安装日志"));

        rightSplit.setTopComponent(detailScroll);
        rightSplit.setBottomComponent(logScroll);
        rightSplit.setDividerLocation(280);
        rightSplit.setResizeWeight(0.5);

        // 主分割面板
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setLeftComponent(listScroll);
        mainSplit.setRightComponent(rightSplit);
        mainSplit.setDividerLocation(320);
        mainSplit.setResizeWeight(0.35);

        // 底部：安装按钮 + 状态 + 进度条
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
        bottomPanel.setBorder(new EmptyBorder(6, 10, 10, 10));

        installButton = new JButton("安装选中软件包");
        installButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        installButton.setEnabled(false);
        installButton.addActionListener(e -> doInstall());

        statusLabel = new JLabel("就绪");
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(true);
        progressBar.setString("");

        JPanel statusPanel = new JPanel(new BorderLayout(8, 0));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);

        bottomPanel.add(installButton, BorderLayout.WEST);
        bottomPanel.add(statusPanel, BorderLayout.CENTER);

        // 组装
        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    // ========== 搜索 ==========

    private void doSearch() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "请输入搜索关键词", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        searchButton.setEnabled(false);
        installButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("搜索中...");
        statusLabel.setText("正在搜索 \"" + keyword + "\" ...");
        listModel.clear();
        detailArea.setText("");
        selectedPackage = null;

        SwingWorker<List<PackageInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<PackageInfo> doInBackground() throws Exception {
                return aptManager.search(keyword);
            }

            @Override
            protected void done() {
                try {
                    List<PackageInfo> results = get();
                    if (results.isEmpty()) {
                        statusLabel.setText("未找到匹配 \"" + keyword + "\" 的软件包");
                        detailArea.setText("未找到匹配的软件包。\n\n提示：\n"
                                + " - 尝试更短或更通用的关键词\n"
                                + " - 点击「刷新源」更新软件包列表\n"
                                + " - 确认包名拼写是否正确");
                    } else {
                        for (PackageInfo pkg : results) {
                            listModel.addElement(pkg);
                        }
                        statusLabel.setText("找到 " + results.size() + " 个匹配的软件包");
                        // 自动选中第一个
                        if (!results.isEmpty()) {
                            resultList.setSelectedIndex(0);
                        }
                    }
                } catch (Exception ex) {
                    statusLabel.setText("搜索失败: " + ex.getMessage());
                    detailArea.setText("搜索失败：\n" + ex.getMessage());
                } finally {
                    searchButton.setEnabled(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setString("");
                }
            }
        };
        worker.execute();
    }

    // ========== 显示详情 ==========

    private void showPackageDetail() {
        PackageInfo pkg = resultList.getSelectedValue();
        if (pkg == null) {
            installButton.setEnabled(false);
            return;
        }

        selectedPackage = pkg;
        installButton.setEnabled(true);
        detailArea.setText("正在查询 " + pkg.getName() + " 的详细信息...");

        SwingWorker<PackageInfo, Void> worker = new SwingWorker<>() {
            @Override
            protected PackageInfo doInBackground() throws Exception {
                return aptManager.show(pkg.getName());
            }

            @Override
            protected void done() {
                try {
                    PackageInfo detail = get();
                    if (detail == null) {
                        detailArea.setText("无法获取 " + pkg.getName() + " 的详细信息。");
                        return;
                    }
                    selectedPackage = detail;
                    StringBuilder sb = new StringBuilder();
                    sb.append("包名:    ").append(detail.getName()).append("\n");
                    sb.append("版本:    ").append(detail.getVersion()).append("\n");
                    sb.append("分类:    ").append(detail.getSection()).append("\n");
                    sb.append("架构:    ").append(detail.getArchitecture()).append("\n");
                    sb.append("安装大小: ").append(detail.getInstalledSize()).append(" KB\n");
                    sb.append("状态:    ").append(detail.isInstalled() ? "已安装 ✓" : "未安装").append("\n");
                    sb.append("依赖:    ").append(truncate(detail.getDepends(), 200)).append("\n");
                    sb.append("\n---- 描述 ----\n");
                    sb.append(detail.getLongDescription().isEmpty()
                            ? detail.getDescription() : detail.getLongDescription());

                    detailArea.setText(sb.toString());
                    detailArea.setCaretPosition(0);

                    if (detail.isInstalled()) {
                        installButton.setText("重新安装 " + detail.getName());
                    } else {
                        installButton.setText("安装 " + detail.getName());
                    }
                } catch (Exception ex) {
                    detailArea.setText("查询详情失败：\n" + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    // ========== 安装 ==========

    private void doInstall() {
        if (selectedPackage == null) {
            return;
        }

        String pkgName = selectedPackage.getName();
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定要安装软件包 \"" + pkgName + "\" 吗？\n\n"
                        + "安装过程需要管理员权限，将弹出密码认证窗口。",
                "确认安装",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        installButton.setEnabled(false);
        searchButton.setEnabled(false);
        updateButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("安装中...");
        statusLabel.setText("正在安装 " + pkgName + " ...");
        logArea.setText("");
        appendLog("=== 开始安装 " + pkgName + " ===");

        SwingWorker<Integer, String> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return aptManager.install(pkgName, this::publish);
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    appendLog(line);
                }
            }

            @Override
            protected void done() {
                try {
                    int exitCode = get();
                    if (exitCode == 0) {
                        appendLog("=== 安装成功 ===");
                        statusLabel.setText(pkgName + " 安装成功");
                        JOptionPane.showMessageDialog(AptGui.this,
                                "软件包 " + pkgName + " 安装成功！",
                                "安装完成", JOptionPane.INFORMATION_MESSAGE);
                        // 刷新详情状态
                        showPackageDetail();
                    } else {
                        appendLog("=== 安装失败（退出码: " + exitCode + "）===");
                        statusLabel.setText(pkgName + " 安装失败（退出码: " + exitCode + "）");
                        JOptionPane.showMessageDialog(AptGui.this,
                                "安装失败，退出码: " + exitCode + "\n请查看下方日志了解详情。",
                                "安装失败", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    appendLog("=== 安装异常: " + ex.getMessage() + " ===");
                    statusLabel.setText("安装异常: " + ex.getMessage());
                } finally {
                    installButton.setEnabled(true);
                    searchButton.setEnabled(true);
                    updateButton.setEnabled(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setString("");
                }
            }
        };
        worker.execute();
    }

    // ========== 刷新源 ==========

    private void doUpdate() {
        updateButton.setEnabled(false);
        searchButton.setEnabled(false);
        installButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("刷新中...");
        statusLabel.setText("正在刷新软件源...");
        logArea.setText("");
        appendLog("=== 执行 apt-get update ===");

        SwingWorker<Integer, String> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return aptManager.update(this::publish);
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    appendLog(line);
                }
            }

            @Override
            protected void done() {
                try {
                    int exitCode = get();
                    if (exitCode == 0) {
                        appendLog("=== 软件源刷新成功 ===");
                        statusLabel.setText("软件源刷新成功");
                    } else {
                        appendLog("=== 刷新失败（退出码: " + exitCode + "）===");
                        statusLabel.setText("刷新失败（退出码: " + exitCode + "）");
                    }
                } catch (Exception ex) {
                    appendLog("=== 刷新异常: " + ex.getMessage() + " ===");
                    statusLabel.setText("刷新异常: " + ex.getMessage());
                } finally {
                    updateButton.setEnabled(true);
                    searchButton.setEnabled(true);
                    installButton.setEnabled(selectedPackage != null);
                    progressBar.setIndeterminate(false);
                    progressBar.setString("");
                }
            }
        };
        worker.execute();
    }

    // ========== 工具方法 ==========

    private void appendLog(String line) {
        logArea.append(line + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    // ========== 入口 ==========

    public static void main(String[] args) {
        // 使用系统外观
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // 回退默认外观
        }

        // 在 EDT 线程启动 GUI
        SwingUtilities.invokeLater(AptGui::new);
    }
}
