package com.aptgui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * APT 图形化软件管理器主界面（v1.1）。
 *
 * 功能：
 *   1. 搜索框搜索 APT 源中的软件包
 *   2. 点击结果查看软件包详情
 *   3. 一键安装（通过 pkexec 提权）
 *   4. 实时显示操作日志
 *   5. 「更多」菜单：APT 修复、添加软件源、卸载软件包、清理无用依赖、系统升级
 */
public class AptGui extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final String APP_VERSION = "1.1";

    private final AptManager aptManager = new AptManager();

    // UI 组件
    private JTextField searchField;
    private JButton searchButton;
    private JButton updateButton;
    private JButton moreButton;
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
        super("APT 软件管理器 v" + APP_VERSION);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(920, 660);
        setMinimumSize(new Dimension(720, 520));
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

        moreButton = new JButton("更多 ▾");
        moreButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        moreButton.addActionListener(e -> showMoreMenu());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttonPanel.add(searchButton);
        buttonPanel.add(updateButton);
        buttonPanel.add(moreButton);

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
        logScroll.setBorder(new TitledBorder("操作日志"));

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

    // ========== 更多菜单 ==========

    private void showMoreMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem fixItem = new JMenuItem("APT 修复（修复损坏的依赖）");
        fixItem.addActionListener(e -> doAptFix());
        menu.add(fixItem);

        JMenuItem addSourceItem = new JMenuItem("添加软件源...");
        addSourceItem.addActionListener(e -> doAddRepository());
        menu.add(addSourceItem);

        menu.addSeparator();

        JMenuItem uninstallItem = new JMenuItem("卸载已安装的软件包...");
        uninstallItem.addActionListener(e -> showUninstallDialog());
        menu.add(uninstallItem);

        JMenuItem autoremoveItem = new JMenuItem("清理无用依赖（autoremove）");
        autoremoveItem.addActionListener(e -> doAutoremove());
        menu.add(autoremoveItem);

        menu.addSeparator();

        JMenuItem upgradeItem = new JMenuItem("升级全部软件包（upgrade）");
        upgradeItem.addActionListener(e -> doUpgrade());
        menu.add(upgradeItem);

        menu.show(moreButton, 0, moreButton.getHeight());
    }

    // ---------- APT 修复 ----------

    private void doAptFix() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "将执行「apt-get install -f」修复损坏的软件包依赖。\n\n"
                        + "需要管理员权限，将弹出密码认证窗口。",
                "APT 修复", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        setBusy(true);
        logArea.setText("");
        appendLog("=== 开始 APT 修复（install -f）===");

        SwingWorker<Integer, String> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return aptManager.fixBroken(this::publish);
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
                    int code = get();
                    if (code == 0) {
                        appendLog("=== APT 修复完成 ===");
                        statusLabel.setText("APT 修复完成");
                    } else {
                        appendLog("=== APT 修复失败（退出码: " + code + "）===");
                        statusLabel.setText("APT 修复失败（退出码: " + code + "）");
                    }
                } catch (Exception ex) {
                    appendLog("=== APT 修复异常: " + ex.getMessage() + " ===");
                    statusLabel.setText("APT 修复异常");
                } finally {
                    setBusy(false);
                }
            }
        };
        worker.execute();
    }

    // ---------- 添加软件源 ----------

    private void doAddRepository() {
        String source = JOptionPane.showInputDialog(this,
                "输入要添加的软件源：\n"
                        + "支持 PPA 或源行，例如：\n"
                        + "  ppa:graphics-drivers/ppa\n"
                        + "  deb http://mirrors.aliyun.com/ubuntu/ focal main universe",
                "添加软件源", JOptionPane.QUESTION_MESSAGE);
        if (source == null || source.trim().isEmpty()) {
            return;
        }
        final String repo = source.trim();

        int confirm = JOptionPane.showConfirmDialog(this,
                "将执行：add-apt-repository -y \"" + repo + "\"\n\n"
                        + "需要管理员权限，将弹出密码认证窗口。",
                "确认添加软件源", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        setBusy(true);
        logArea.setText("");
        appendLog("=== 添加软件源: " + repo + " ===");

        SwingWorker<Integer, String> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return aptManager.addRepository(repo, this::publish);
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
                    int code = get();
                    if (code == 0) {
                        appendLog("=== 软件源添加成功 ===");
                        statusLabel.setText("软件源添加成功");
                        // 提示刷新源
                        int choice = JOptionPane.showConfirmDialog(AptGui.this,
                                "软件源添加成功。是否立即刷新软件源（apt-get update）？",
                                "刷新软件源", JOptionPane.YES_NO_OPTION,
                                JOptionPane.QUESTION_MESSAGE);
                        if (choice == JOptionPane.YES_OPTION) {
                            doUpdate();
                            return;
                        }
                    } else {
                        appendLog("=== 软件源添加失败（退出码: " + code + "）===");
                        statusLabel.setText("软件源添加失败（退出码: " + code + "）");
                        JOptionPane.showMessageDialog(AptGui.this,
                                "添加失败（退出码: " + code + "）。\n"
                                        + "请确认已安装 software-properties-common：\n"
                                        + "sudo apt install software-properties-common\n"
                                        + "并检查源格式是否正确。",
                                "添加失败", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    appendLog("=== 添加软件源异常: " + ex.getMessage() + " ===");
                    statusLabel.setText("添加软件源异常");
                } finally {
                    setBusy(false);
                }
            }
        };
        worker.execute();
    }

    // ---------- 卸载软件包 ----------

    private void showUninstallDialog() {
        statusLabel.setText("正在加载已安装软件包列表...");
        progressBar.setIndeterminate(true);
        progressBar.setString("加载中...");
        moreButton.setEnabled(false);

        SwingWorker<List<PackageInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<PackageInfo> doInBackground() throws Exception {
                return aptManager.listInstalled();
            }

            @Override
            protected void done() {
                try {
                    List<PackageInfo> installed = get();
                    if (installed.isEmpty()) {
                        JOptionPane.showMessageDialog(AptGui.this,
                                "未获取到已安装的软件包列表。",
                                "提示", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    showInstalledChooser(installed);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(AptGui.this,
                            "加载已安装列表失败：\n" + ex.getMessage(),
                            "错误", JOptionPane.ERROR_MESSAGE);
                } finally {
                    progressBar.setIndeterminate(false);
                    progressBar.setString("");
                    moreButton.setEnabled(true);
                    statusLabel.setText("就绪");
                }
            }
        };
        worker.execute();
    }

    /** 弹出已安装软件包选择框（带过滤），选中后确认卸载 */
    private void showInstalledChooser(List<PackageInfo> installed) {
        DefaultListModel<PackageInfo> model = new DefaultListModel<>();
        for (PackageInfo pkg : installed) {
            model.addElement(pkg);
        }
        JList<PackageInfo> list = new JList<>(model);
        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // 显示 "包名 (版本)"
        list.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(
                        l, value, index, isSelected, cellHasFocus);
                if (value instanceof PackageInfo) {
                    PackageInfo p = (PackageInfo) value;
                    setText(p.getName() + "  (" + p.getVersion() + ")");
                }
                return c;
            }
        });

        JTextField filterField = new JTextField();
        filterField.putClientProperty("JTextField.placeholderText", "输入名称过滤...");
        filterField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String kw = filterField.getText().trim().toLowerCase();
                model.clear();
                for (PackageInfo pkg : installed) {
                    if (kw.isEmpty() || pkg.getName().toLowerCase().contains(kw)) {
                        model.addElement(pkg);
                    }
                }
                if (!model.isEmpty()) {
                    list.setSelectedIndex(0);
                }
            }
        });

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(filterField, BorderLayout.NORTH);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(420, 420));

        int result = JOptionPane.showConfirmDialog(this, panel,
                "选择要卸载的软件包（共 " + installed.size() + " 个已安装）",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        PackageInfo selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        doUninstall(selected.getName());
    }

    private void doUninstall(String pkgName) {
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定要卸载软件包 \"" + pkgName + "\" 吗？\n\n"
                        + "⚠ 警告：\n"
                        + " - 依赖它的软件包也会被一并移除\n"
                        + " - 请勿卸载系统关键软件包（如 ubuntu-desktop、kernel、libc6 等）\n"
                        + " - 卸载需要管理员权限，将弹出密码认证窗口",
                "确认卸载", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        setBusy(true);
        logArea.setText("");
        appendLog("=== 开始卸载 " + pkgName + " ===");

        SwingWorker<Integer, String> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return aptManager.uninstall(pkgName, this::publish);
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
                    int code = get();
                    if (code == 0) {
                        appendLog("=== 卸载完成 ===");
                        statusLabel.setText(pkgName + " 已卸载");
                        JOptionPane.showMessageDialog(AptGui.this,
                                "软件包 " + pkgName + " 卸载成功！",
                                "卸载完成", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        appendLog("=== 卸载失败（退出码: " + code + "）===");
                        statusLabel.setText(pkgName + " 卸载失败（退出码: " + code + "）");
                        JOptionPane.showMessageDialog(AptGui.this,
                                "卸载失败，退出码: " + code + "\n请查看下方日志了解详情。",
                                "卸载失败", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    appendLog("=== 卸载异常: " + ex.getMessage() + " ===");
                    statusLabel.setText("卸载异常");
                } finally {
                    setBusy(false);
                }
            }
        };
        worker.execute();
    }

    // ---------- 清理无用依赖 ----------

    private void doAutoremove() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "将执行「apt-get autoremove」清理不再需要的依赖包。\n\n"
                        + "需要管理员权限，将弹出密码认证窗口。",
                "清理无用依赖", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        setBusy(true);
        logArea.setText("");
        appendLog("=== 开始清理无用依赖（autoremove）===");

        SwingWorker<Integer, String> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return aptManager.autoremove(this::publish);
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
                    int code = get();
                    if (code == 0) {
                        appendLog("=== 清理完成 ===");
                        statusLabel.setText("无用依赖清理完成");
                    } else {
                        appendLog("=== 清理失败（退出码: " + code + "）===");
                        statusLabel.setText("清理失败（退出码: " + code + "）");
                    }
                } catch (Exception ex) {
                    appendLog("=== 清理异常: " + ex.getMessage() + " ===");
                    statusLabel.setText("清理异常");
                } finally {
                    setBusy(false);
                }
            }
        };
        worker.execute();
    }

    // ---------- 系统升级 ----------

    private void doUpgrade() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "将执行「apt-get upgrade」升级所有可升级的软件包。\n\n"
                        + "需要管理员权限，将弹出密码认证窗口。\n"
                        + "建议先点击「刷新源」更新软件包列表。",
                "升级全部软件包", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        setBusy(true);
        logArea.setText("");
        appendLog("=== 开始升级全部软件包（upgrade）===");

        SwingWorker<Integer, String> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return aptManager.upgrade(this::publish);
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
                    int code = get();
                    if (code == 0) {
                        appendLog("=== 升级完成 ===");
                        statusLabel.setText("系统升级完成");
                    } else {
                        appendLog("=== 升级失败（退出码: " + code + "）===");
                        statusLabel.setText("升级失败（退出码: " + code + "）");
                    }
                } catch (Exception ex) {
                    appendLog("=== 升级异常: " + ex.getMessage() + " ===");
                    statusLabel.setText("升级异常");
                } finally {
                    setBusy(false);
                }
            }
        };
        worker.execute();
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

        setBusy(true);
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
                    setBusy(false);
                }
            }
        };
        worker.execute();
    }

    // ========== 刷新源 ==========

    private void doUpdate() {
        setBusy(true);
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
                    setBusy(false);
                }
            }
        };
        worker.execute();
    }

    // ========== 工具方法 ==========

    /** 切换繁忙状态：禁用/启用操作按钮，控制进度条 */
    private void setBusy(boolean busy) {
        searchButton.setEnabled(!busy);
        updateButton.setEnabled(!busy);
        moreButton.setEnabled(!busy);
        installButton.setEnabled(!busy && selectedPackage != null);
        progressBar.setIndeterminate(busy);
        progressBar.setString(busy ? "操作中..." : "");
        if (busy) {
            statusLabel.setText("操作进行中...");
        }
    }

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
