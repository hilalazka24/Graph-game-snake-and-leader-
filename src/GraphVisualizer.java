package src;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class GraphVisualizer extends JFrame {
    private GraphPanel graphPanel;
    private Graph graph;
    private GameManager gameManager;
    private List<Ladder> ladders;
    private ScoreManager scoreManager;

    public GraphVisualizer() {
        setTitle("Snake & Ladder: Final Node Scoring");
        setSize(1100, 825);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Setup Graph 8x8
        String[] labels = new String[64];
        for (int i = 0; i < 64; i++) {
            labels[i] = String.valueOf(i + 1);
        }

        graph = new Graph(64, labels);
        for (int i = 0; i < 63; i++) {
            graph.addEdge(i, i + 1, 1);
        }

        // Setup Ladders
        ladders = new ArrayList<>();
        ladders.add(new Ladder(2, 21));
        ladders.add(new Ladder(6, 29));
        ladders.add(new Ladder(14, 55));
        ladders.add(new Ladder(35, 48));


        Random rand = new Random();
        int rStart = rand.nextInt(50);
        int rEnd = rStart + rand.nextInt(15) + 3;
        if (rEnd > 63) rEnd = 63;
        ladders.add(new Ladder(rStart, rEnd));

        scoreManager = new ScoreManager();
        gameManager = new GameManager(scoreManager);
        gameManager.setGraph(graph);
        gameManager.setLadders(ladders);

        graphPanel = new GraphPanel(graph, gameManager, ladders, scoreManager);
        getContentPane().add(graphPanel, BorderLayout.CENTER);

        JPanel controlPanel = createControlPanel();
        getContentPane().add(controlPanel, BorderLayout.SOUTH);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Game Controls"));
        panel.setBackground(new Color(240, 240, 240));

        JLabel playersLabel = new JLabel("Players:");
        panel.add(playersLabel);

        SpinnerNumberModel playerSpinnerModel = new SpinnerNumberModel(2, 2, 6, 1);
        JSpinner playerSpinner = new JSpinner(playerSpinnerModel);
        playerSpinner.setPreferredSize(new Dimension(50, 30));
        panel.add(playerSpinner);

        JButton newGameButton = new JButton("New Game");
        newGameButton.setFont(new Font("Arial", Font.BOLD, 14));
        newGameButton.setBackground(new Color(50, 150, 250));
        newGameButton.setForeground(Color.BLACK);
        newGameButton.setFocusPainted(false);
        newGameButton.addActionListener(e -> {
            int numPlayers = (Integer) playerSpinner.getValue();
            gameManager.resetGame(numPlayers);
            scoreManager.resetScores(gameManager.getAllPlayers());
            graphPanel.reset();
        });
        panel.add(newGameButton);

        JButton rollDiceButton = new JButton("Roll Dice");
        rollDiceButton.setFont(new Font("Arial", Font.BOLD, 14));
        rollDiceButton.setBackground(new Color(50, 150, 250));
        rollDiceButton.setForeground(Color.BLACK);
        rollDiceButton.setFocusPainted(false);
        rollDiceButton.addActionListener(e -> {
            SoundManager.playDiceSound();
            int target = gameManager.rollDice();
            if (target >= 0) {
                graphPanel.startPlayerAnimation();
            }
        });
        panel.add(rollDiceButton);

        return panel;
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(GraphVisualizer::new);
    }
}

// ---------------------------------------------------------
// GRAPH PANEL
// ---------------------------------------------------------
class GraphPanel extends JPanel {
    private Graph graph;
    private GameManager gameManager;
    private List<Ladder> ladders;
    private ScoreManager scoreManager;
    private Point[] nodePositions;
    private javax.swing.Timer playerMoveTimer;

    // Assets Gambar
    private BufferedImage boardImage;
    private BufferedImage holeImage;
    private BufferedImage pawnImage;

    // Cache untuk menyimpan outline yang sudah digenerate agar game tidak lag
    private Map<Color, BufferedImage> outlineCache = new HashMap<>();

    // Ukuran Board
    private static final int BOARD_SIZE = 720;
    private static final int GRID_SIZE = 8;
    private static final int CELL_SIZE = BOARD_SIZE / GRID_SIZE;
    private static final int OFFSET_X = 30;
    private static final int OFFSET_Y = 20;

    // Radius Visual
    private static final int NODE_RADIUS = 22;

    // UKURAN PION (Ubah di sini jika ingin membesarkan/mengecilkan)
    private static final int PAWN_WIDTH = 80;
    private static final int PAWN_HEIGHT = 54;
    // Ketebalan Outline
    private static final int OUTLINE_THICKNESS = 2;

    public GraphPanel(Graph graph, GameManager gameManager, List<Ladder> ladders, ScoreManager scoreManager) {
        this.graph = graph;
        this.gameManager = gameManager;
        this.ladders = ladders;
        this.scoreManager = scoreManager;
        this.nodePositions = new Point[graph.size];
        setBackground(Color.WHITE);

        // Load Images
        try {
            boardImage = ImageIO.read(new File("src/Gemini_Generated_Image_qnsky7qnsky7qnsk.png"));
        } catch (IOException e) {
            System.err.println("Error: Background image not found.");
        }
        try {
            holeImage = ImageIO.read(new File("src/Untitled design.png"));
        } catch (IOException e) {
            System.err.println("Error: Hole image not found.");
        }
        try {
            pawnImage = ImageIO.read(new File("src/Untitled design 2.png"));
        } catch (IOException e) {
            System.err.println("Error: Pawn image (pawn_mole.png) not found.");
        }

        calculateNodePositions();
    }

    public void startPlayerAnimation() {
        if (playerMoveTimer != null) playerMoveTimer.stop();
        playerMoveTimer = new javax.swing.Timer(200, e -> {
            boolean stillAnimating = gameManager.updateAnimation();
            repaint();
            if (!stillAnimating) {
                playerMoveTimer.stop();
                checkWinner();
            }
        });
        playerMoveTimer.start();
    }

    // --- FITUR BARU: Membuat Silhouette/Outline mengikuti bentuk gambar ---
    public void reset() {
        outlineCache.clear(); // Bersihkan cache saat reset game
        repaint();
    }

    private BufferedImage createSilhouette(BufferedImage source, Color color) {
        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage silhouette = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int pixel = source.getRGB(x, y);
                // Ambil Alpha (Transparansi)
                int alpha = (pixel >> 24) & 0xff;

                // Jika pixel tidak transparan (ada gambarnya), warnai dengan warna player
                if (alpha > 10) {
                    // Set warna pixel baru dengan alpha asli (untuk antialiasing) tapi warna player
                    int newPixel = (alpha << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
                    silhouette.setRGB(x, y, newPixel);
                }
            }
        }
        return silhouette;
    }

    private void checkWinner() {
        Player winner = gameManager.getWinner();
        if (winner != null) {
            scoreManager.recordWin(winner);
            SoundManager.playWinSound();
            SwingUtilities.invokeLater(() -> {
                String message = winner.name + " wins!\n" +
                        "Final Score: " + winner.totalScore + "\n" +
                        "Session Wins: " + scoreManager.getWinCount(winner.name);
                JOptionPane.showMessageDialog(GraphPanel.this, message,
                        "Game Over", JOptionPane.INFORMATION_MESSAGE);
            });
        }
    }

    private void calculateNodePositions() {
        for (int i = 0; i < 64; i++) {
            int row = i / GRID_SIZE;
            int col = i % GRID_SIZE;
            int y = (OFFSET_Y + BOARD_SIZE) - (row * CELL_SIZE) - (CELL_SIZE / 2);
            int x;
            if (row % 2 == 0) x = OFFSET_X + (col * CELL_SIZE) + (CELL_SIZE / 2);
            else x = OFFSET_X + ((GRID_SIZE - 1 - col) * CELL_SIZE) + (CELL_SIZE / 2);
            nodePositions[i] = new Point(x, y);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        if (boardImage != null) {
            g2.drawImage(boardImage, OFFSET_X, OFFSET_Y, BOARD_SIZE, BOARD_SIZE, null);
        } else {
            g2.setColor(new Color(100, 200, 100));
            g2.fillRect(OFFSET_X, OFFSET_Y, BOARD_SIZE, BOARD_SIZE);
        }

        drawEdges(g2);
        drawNodes(g2);
        drawPlayers(g2);
        drawGameInfo(g2);
        drawDiceInfo(g2);
        drawScoreBoard(g2);
    }

    private void drawEdges(Graphics2D g2) {
        g2.setStroke(new BasicStroke(3));
        for (Ladder ladder : ladders) {
            Point p1 = nodePositions[ladder.startIdx];
            Point p2 = nodePositions[ladder.endIdx];
            g2.setColor(new Color(255, 255, 255, 150));
            g2.drawLine(p1.x, p1.y, p2.x, p2.y);
            g2.setColor(new Color(139, 69, 19));
            g2.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(p1.x, p1.y, p2.x, p2.y);
            drawArrowHead(g2, p2, p1);
        }
    }

    private void drawArrowHead(Graphics2D g2, Point tip, Point tail) {
        double phi = Math.toRadians(40);
        int barb = 12;
        double dy = tip.y - tail.y;
        double dx = tip.x - tail.x;
        double theta = Math.atan2(dy, dx);
        double x, y, rho = theta + phi;
        for(int j = 0; j < 2; j++) {
            x = tip.x - barb * Math.cos(rho);
            y = tip.y - barb * Math.sin(rho);
            g2.draw(new Line2D.Double(tip.x, tip.y, x, y));
            rho = theta - phi;
        }
    }

    private void drawNodes(Graphics2D g2) {
        Set<Integer> ladderNodes = new HashSet<>();
        for (Ladder l : ladders) {
            ladderNodes.add(l.startIdx);
            ladderNodes.add(l.endIdx);
        }

        for (int i = 0; i < graph.size; i++) {
            Point p = nodePositions[i];
            boolean isStar = (i + 1) % 5 == 0;
            boolean isLadderNode = ladderNodes.contains(i);

            if (isLadderNode && holeImage != null) {
                int imgSize = NODE_RADIUS * 2 + 15;
                g2.drawImage(holeImage, p.x - imgSize / 2, p.y - imgSize / 2, imgSize, imgSize, null);
            } else if (isStar) {
                g2.setColor(new Color(255, 215, 0, 150));
                Polygon star = createStar(p.x, p.y, NODE_RADIUS - 8, NODE_RADIUS + 2);
                g2.fillPolygon(star);
            } else {
                g2.setColor(new Color(255, 255, 255, 40));
                g2.fillOval(p.x - NODE_RADIUS, p.y - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);
            }

            String label = graph.label[i];
            g2.setFont(new Font("Arial", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int txtX = p.x - fm.stringWidth(label) / 2;
            int txtY = p.y + 4;
            g2.setColor(new Color(0, 0, 0, 200));
            g2.drawString(label, txtX + 1, txtY + 1);
            g2.setColor(Color.WHITE);
            g2.drawString(label, txtX, txtY);

            g2.setFont(new Font("Arial", Font.BOLD, 9));
            g2.setColor(new Color(255, 255, 200));
            String scoreText = "+" + scoreManager.getNodeScore(i);
            fm = g2.getFontMetrics();
            g2.drawString(scoreText, p.x - fm.stringWidth(scoreText) / 2, p.y + 16);
        }
    }

    private Polygon createStar(int centerX, int centerY, int innerRadius, int outerRadius) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-90 + i * 36);
            int r = (i % 2 == 0) ? outerRadius : innerRadius;
            int x = (int) (centerX + r * Math.cos(angle));
            int y = (int) (centerY + r * Math.sin(angle));
            polygon.addPoint(x, y);
        }
        return polygon;
    }

    // --- PENGGAMBARAN PLAYER DENGAN OUTLINE KONTUR ---
    private void drawPlayers(Graphics2D g2) {
        List<Player> players = gameManager.getAllPlayers();
        Map<Integer, Integer> playersOnNode = new HashMap<>();

        for (Player player : players) {
            Point nodePos = nodePositions[player.position];
            int count = playersOnNode.getOrDefault(player.position, 0);
            playersOnNode.put(player.position, count + 1);

            // Offset agar tidak bertumpuk
            int offsetX = (count % 3) * 12 - 6;
            int offsetY = (count / 3) * 12 - 6;
            int finalX = nodePos.x + offsetX;
            int finalY = nodePos.y + offsetY;

            int drawX = finalX - PAWN_WIDTH / 2;
            int drawY = finalY - PAWN_HEIGHT / 2;

            if (pawnImage != null) {
                // 1. Cek apakah outline sudah ada di cache
                BufferedImage outline = outlineCache.get(player.color);

                // 2. Jika belum ada, buat silhouette outline
                if (outline == null) {
                    outline = createSilhouette(pawnImage, player.color);
                    outlineCache.put(player.color, outline);
                }

                // 3. Gambar Outline (Digeser ke 8 arah untuk efek stroke tebal)
                // Ini menciptakan efek "Stroke" di sekeliling bentuk gambar
                int t = OUTLINE_THICKNESS;
                g2.drawImage(outline, drawX - t, drawY, PAWN_WIDTH, PAWN_HEIGHT, null);
                g2.drawImage(outline, drawX + t, drawY, PAWN_WIDTH, PAWN_HEIGHT, null);
                g2.drawImage(outline, drawX, drawY - t, PAWN_WIDTH, PAWN_HEIGHT, null);
                g2.drawImage(outline, drawX, drawY + t, PAWN_WIDTH, PAWN_HEIGHT, null);
                // Diagonal untuk mengisi sudut
                g2.drawImage(outline, drawX - t, drawY - t, PAWN_WIDTH, PAWN_HEIGHT, null);
                g2.drawImage(outline, drawX + t, drawY + t, PAWN_WIDTH, PAWN_HEIGHT, null);
                g2.drawImage(outline, drawX - t, drawY + t, PAWN_WIDTH, PAWN_HEIGHT, null);
                g2.drawImage(outline, drawX + t, drawY - t, PAWN_WIDTH, PAWN_HEIGHT, null);

                // 4. Gambar Pion Asli di atas outline
                g2.drawImage(pawnImage, drawX, drawY, PAWN_WIDTH, PAWN_HEIGHT, null);

            } else {
                // Fallback (Bulat)
                int radius = 10;
                g2.setColor(player.color);
                g2.fillOval(finalX - radius, finalY - radius, radius * 2, radius * 2);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(finalX - radius, finalY - radius, radius * 2, radius * 2);
            }
        }
    }
    // ------------------------------------------------

    private void drawGameInfo(Graphics2D g2) {
        int x = OFFSET_X + BOARD_SIZE + 30;
        int y = 50;
        g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
        g2.setColor(new Color(50, 50, 50));
        g2.drawString("Game Status", x, y);
        y += 30;
        Player currentPlayer = gameManager.getCurrentPlayer();
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        g2.drawString("Current Turn:", x, y);
        y += 25;
        g2.setColor(currentPlayer.color);
        g2.fillRoundRect(x, y - 18, 120, 25, 10, 10);
        g2.setColor(Color.WHITE);
        if (currentPlayer.color.equals(Color.YELLOW) || currentPlayer.color.equals(Color.CYAN)) {
            g2.setColor(Color.BLACK);
        }
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2.drawString(currentPlayer.name, x + 10, y);
        y += 30;
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        g2.drawString("Roll Count: " + currentPlayer.rollCount, x, y);
        y += 20;
        g2.drawString("Total Score: " + currentPlayer.totalScore, x, y);
        y += 25;
        boolean isActive = currentPlayer.isShortestPathActive;
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2.setColor(isActive ? new Color(0, 150, 0) : Color.RED);
        g2.drawString(isActive ? "SHORTCUT UNLOCKED" : "LOCKED", x, y);
    }

    private void drawDiceInfo(Graphics2D g2) {
        if (gameManager.getLastDiceRoll() == 0) return;
        int x = OFFSET_X + BOARD_SIZE + 30;
        int y = 250;
        g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
        g2.setColor(Color.BLACK);
        g2.drawString("Last Roll:", x, y);
        y += 25;
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(x, y, 60, 60, 15, 15);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(x, y, 60, 60, 15, 15);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 36));
        String diceValue = String.valueOf(gameManager.getLastDiceRoll());
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(diceValue, x + 30 - fm.stringWidth(diceValue)/2, y + 43);
        y += 80;
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        if (gameManager.wasLastMoveForward()) {
            g2.setColor(new Color(0, 128, 0));
            g2.drawString("Moving Forward >>", x, y);
        } else {
            g2.setColor(Color.RED);
            g2.drawString("<< Moving Backward", x, y);
        }
    }

    private void drawScoreBoard(Graphics2D g2) {
        int x = OFFSET_X + BOARD_SIZE + 30;
        int y = 420;
        g2.setColor(new Color(50, 50, 50));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
        g2.drawString("Leaderboard", x, y);
        y += 10;
        g2.setStroke(new BasicStroke(1));
        g2.drawLine(x, y, x + 150, y);
        y += 20;
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        List<Player> leaderboard = scoreManager.getLeaderboard(gameManager.getAllPlayers());
        for (int i = 0; i < leaderboard.size(); i++) {
            Player p = leaderboard.get(i);
            g2.setColor(p.color);
            g2.fillOval(x, y - 10, 10, 10);
            g2.setColor(Color.BLACK);
            g2.drawString((i + 1) + ". " + p.name + " (" + p.totalScore + ")", x + 15, y);
            y += 20;
        }
    }
}

// ---------------------------------------------------------
// SUPPORT CLASSES
// ---------------------------------------------------------
class SoundManager {
    public static void playDiceSound() { playSound("dice.wav"); }
    public static void playWinSound() { playSound("win.wav"); }
    private static void playSound(String filename) {
        new Thread(() -> {
            try {
                File soundFile = new File("src/" + filename);
                if (!soundFile.exists()) return;
                AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundFile);
                Clip clip = AudioSystem.getClip();
                clip.open(audioIn);
                clip.start();
            } catch (Exception ignored) {}
        }).start();
    }
}

class ScoreManager {
    private Map<String, Integer> sessionWins;
    private Map<Integer, Integer> nodeScores;
    private Random random;
    public ScoreManager() {
        sessionWins = new HashMap<>();
        nodeScores = new HashMap<>();
        random = new Random();
        initializeNodeScores();
    }
    private void initializeNodeScores() {
        for (int i = 0; i < 64; i++) {
            nodeScores.put(i, random.nextInt(10) + 1);
        }
    }
    public int getNodeScore(int nodeIndex) { return nodeScores.getOrDefault(nodeIndex, 1); }
    public void addScore(Player player, int nodeIndex) {
        int score = getNodeScore(nodeIndex);
        player.totalScore += score;
    }
    public void recordWin(Player player) { sessionWins.put(player.name, sessionWins.getOrDefault(player.name, 0) + 1); }
    public int getWinCount(String playerName) { return sessionWins.getOrDefault(playerName, 0); }
    public void resetScores(List<Player> players) { for (Player p : players) p.totalScore = 0; }
    public List<Player> getLeaderboard(List<Player> players) {
        List<Player> sorted = new ArrayList<>(players);
        sorted.sort((p1, p2) -> Integer.compare(p2.totalScore, p1.totalScore));
        return sorted;
    }
    public Map<String, Integer> getSessionWins() { return sessionWins; }
}

class Ladder {
    int startIdx, endIdx;
    public Ladder(int start, int end) { this.startIdx = start; this.endIdx = end; }
}

class GameManager {
    private List<Player> allPlayers;
    private Deque<Player> turnQueue;
    private List<Ladder> gameLadders;
    private Graph graph;
    private ScoreManager scoreManager;
    private Random random;
    private int lastDiceRoll;
    private boolean lastMoveForward;
    private boolean isAnimating;
    private static final Color[] PLAYER_COLORS = {
            new Color(220, 20, 60), new Color(30, 144, 255),
            new Color(50, 205, 50), new Color(255, 215, 0),
            new Color(138, 43, 226), new Color(255, 140, 0)
    };
    public GameManager(ScoreManager scoreManager) {
        this.scoreManager = scoreManager;
        allPlayers = new ArrayList<>();
        turnQueue = new LinkedList<>();
        gameLadders = new ArrayList<>();
        initializePlayers(2);
        random = new Random();
        isAnimating = false;
    }
    private void initializePlayers(int numPlayers) {
        allPlayers.clear();
        turnQueue.clear();
        for (int i = 0; i < numPlayers; i++) {
            allPlayers.add(new Player("Player " + (i + 1), PLAYER_COLORS[i % PLAYER_COLORS.length], 0));
        }
        turnQueue.addAll(allPlayers);
    }
    public void setGraph(Graph graph) { this.graph = graph; }
    public void setLadders(List<Ladder> ladders) { this.gameLadders = ladders; }
    private boolean isPrime(int n) {
        if (n <= 1) return false;
        for (int i = 2; i <= Math.sqrt(n); i++) if (n % i == 0) return false;
        return true;
    }
    public int rollDice() {
        if (getWinner() != null || isAnimating) return -1;
        Player currentPlayer = turnQueue.peek();
        currentPlayer.rollCount++;
        lastDiceRoll = random.nextInt(6) + 1;
        lastMoveForward = random.nextDouble() < 0.8;
        currentPlayer.plannedPath.clear();
        List<Integer> simulationStack = new ArrayList<>(currentPlayer.path);
        for (int i = 0; i < lastDiceRoll; i++) {
            int currentPos = simulationStack.get(simulationStack.size() - 1);
            int nextPos;
            if (lastMoveForward) {
                int ladderEnd = -1;
                if (currentPlayer.isShortestPathActive) {
                    for (Ladder l : gameLadders) {
                        if (l.startIdx == currentPos) {
                            ladderEnd = l.endIdx;
                            break;
                        }
                    }
                }
                nextPos = (ladderEnd != -1) ? ladderEnd : currentPos + 1;
                if (nextPos > 63) nextPos = 63;
                if (nextPos != currentPos) simulationStack.add(nextPos);
            } else {
                if (simulationStack.size() > 1) {
                    simulationStack.remove(simulationStack.size() - 1);
                    nextPos = simulationStack.get(simulationStack.size() - 1);
                } else {
                    nextPos = 0;
                }
            }
            currentPlayer.plannedPath.add(nextPos);
        }
        isAnimating = true;
        return simulationStack.get(simulationStack.size() - 1);
    }
    public boolean updateAnimation() {
        if (!isAnimating) return false;
        Player currentPlayer = turnQueue.peek();
        if (!currentPlayer.plannedPath.isEmpty()) {
            int nextStep = currentPlayer.plannedPath.remove(0);
            boolean isBacktracking = false;
            if (currentPlayer.path.size() > 1 && nextStep == currentPlayer.path.get(currentPlayer.path.size() - 2)) {
                isBacktracking = true;
            }
            if (isBacktracking) {
                currentPlayer.path.remove(currentPlayer.path.size() - 1);
            } else {
                if (currentPlayer.path.isEmpty() || currentPlayer.path.get(currentPlayer.path.size() - 1) != nextStep) {
                    currentPlayer.path.add(nextStep);
                }
            }
            currentPlayer.position = nextStep;
            return true;
        }
        scoreManager.addScore(currentPlayer, currentPlayer.position);
        boolean isPrimeSpot = isPrime(currentPlayer.position + 1);
        if (isPrimeSpot && currentPlayer.rollCount >= 2 && !currentPlayer.isShortestPathActive) {
            currentPlayer.isShortestPathActive = true;
        }
        isAnimating = false;
        if ((currentPlayer.position + 1) % 5 != 0 || currentPlayer.position == 63) {
            turnQueue.offer(turnQueue.poll());
        }
        return false;
    }
    public Player getWinner() {
        for (Player p : allPlayers) if (p.position == 63) return p;
        return null;
    }
    public void resetGame(int numPlayers) {
        initializePlayers(numPlayers);
        lastDiceRoll = 0;
        isAnimating = false;
    }
    public List<Player> getAllPlayers() { return allPlayers; }
    public Player getCurrentPlayer() { return turnQueue.peek(); }
    public int getLastDiceRoll() { return lastDiceRoll; }
    public boolean wasLastMoveForward() { return lastMoveForward; }
}

class Player {
    String name;
    Color color;
    int position;
    List<Integer> path;
    List<Integer> plannedPath;
    int rollCount;
    boolean isShortestPathActive;
    int totalScore;
    public Player(String name, Color color, int position) {
        this.name = name;
        this.color = color;
        this.position = position;
        this.path = new ArrayList<>();
        this.path.add(position);
        this.plannedPath = new ArrayList<>();
        this.rollCount = 0;
        this.isShortestPathActive = false;
        this.totalScore = 0;
    }
}

class Graph {
    int[][] g;
    String[] label;
    int size;
    public Graph(int size, String[] labels) {
        this.size = size;
        this.label = labels;
        this.g = new int[size][size];
    }
    public void addEdge(int from, int to, int weight) { g[from][to] = weight; }
}