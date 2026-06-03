using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace SnakeGame;

public partial class SnakeForm : Form
{
    private List<Point> _snake = null!;
    private List<Point> _obstacles = null!;
    private List<FoodItem> _foods = new();
    private Direction _currentDirection;
    private bool _isGameOver;
    private bool _isPaused;
    private int _score;
    private int _pendingGrowth = 0;
    private int _gridSize; // Size of each cell
    private int _width = 45; // Number of cells horizontally
    private int _height = 30; // Number of cells vertically
    private System.Windows.Forms.Timer _gameTimer = null!;
    private Random _random = null!;

    private Label _lblScore = null!;
    private Button _btnStart = null!;
    private Button _btnPause = null!;
    private PictureBox _gameCanvas = null!;
    private Panel _levelPanel = null!;
    private RadioButton[] _levelButtons = null!;
    private RadioButton[] _sizeButtons = null!;
    private int _selectedLevel = 1;
    private int _selectedSizeIndex = 1; // default = 45x30
    private const int PanelWidth = 170;

    // Minimum cell size to keep things playable
    private const int MinGridSize = 6;
    private const int MaxGridSize = 40;

    private static readonly string[] LevelNames = { "Beginner", "Adventurer", "Warrior", "Champion", "Legend" };

    // Board size presets: name, width in cells, height in cells
    private static readonly (string Name, int W, int H)[] BoardSizes =
    {
        ("Pocket",     30,  20),
        ("Classic",    45,  30),
        ("Grand",      60,  40),
        ("Colossal",   75,  50),
        ("Infinite",  120,  80),
    };

    private static int GetLevelObstacleCount(int level) => level switch
    {
        1 => 0,
        2 => 8,
        3 => 15,
        4 => 25,
        5 => 40,
        _ => 15
    };

    private static int GetLevelSpeed(int level) => level switch
    {
        1 => 140,
        2 => 120,
        3 => 100,
        4 => 80,
        5 => 60,
        _ => 100
    };

    private enum Direction
    {
        Up,
        Down,
        Left,
        Right
    }

    public SnakeForm()
    {
        InitializeComponent();
        InitializeGameUI();
        _random = new Random();
    }

    private void InitializeGameUI()
    {
        var screen = Screen.PrimaryScreen!.WorkingArea;
        int targetWidth = Math.Max(screen.Width / 2, 800);
        int targetHeight = Math.Max(screen.Height / 2, 450);

        _gridSize = ComputeGridSize(targetWidth, targetHeight, _width, _height);

        this.Text = "Snake Game";
        this.ClientSize = new Size(PanelWidth + _width * _gridSize + 40, _height * _gridSize + 80);
        this.FormBorderStyle = FormBorderStyle.Sizable;
        this.MinimumSize = new Size(PanelWidth + _width * MinGridSize + 60, _height * MinGridSize + 120);
        this.StartPosition = FormStartPosition.CenterScreen;

        _lblScore = new Label
        {
            Text = "Score: 0",
            Location = new Point(PanelWidth + 10, 10),
            AutoSize = true,
            Font = new Font("Arial", 14, FontStyle.Bold)
        };
        this.Controls.Add(_lblScore);

        _btnPause = new Button
        {
            Text = "⏸ Pause",
            Size = new Size(110, 30),
            Font = new Font("Arial", 10, FontStyle.Bold),
            TabStop = false,
            Visible = false,
            BackColor = Color.FromArgb(60, 60, 60),
            ForeColor = Color.White,
            FlatStyle = FlatStyle.Flat,
            Anchor = AnchorStyles.Top | AnchorStyles.Right
        };
        _btnPause.FlatAppearance.BorderColor = Color.Gray;
        _btnPause.Click += (s, e) => TogglePause();
        this.Controls.Add(_btnPause);

        _btnStart = new Button
        {
            Text = "\uD83C\uDFAE New Game",
            Size = new Size(120, 30),
            Font = new Font("Arial", 10, FontStyle.Bold),
            TabStop = false,
            Anchor = AnchorStyles.Top | AnchorStyles.Right
        };
        _btnStart.Click += (s, e) => StartGame();
        this.Controls.Add(_btnStart);

        _gameCanvas = new PictureBox
        {
            BackColor = Color.Black,
            BorderStyle = BorderStyle.Fixed3D
        };
        _gameCanvas.Paint += GameCanvas_Paint!;
        this.Controls.Add(_gameCanvas);

        _gameTimer = new System.Windows.Forms.Timer();
        _gameTimer.Interval = 100;
        _gameTimer.Tick += GameTimer_Tick!;

        this.KeyPreview = true;

        // Level selection panel (left side)
        _levelPanel = new Panel
        {
            Location = new Point(0, 0),
            Size = new Size(PanelWidth, this.ClientSize.Height),
            BackColor = Color.FromArgb(20, 20, 20),
            Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left,
            AutoScroll = true
        };

        var panelTitle = new Label
        {
            Text = "⚔ LEVEL",
            ForeColor = Color.White,
            Font = new Font("Arial", 10, FontStyle.Bold),
            AutoSize = false,
            TextAlign = ContentAlignment.MiddleCenter,
            Size = new Size(PanelWidth, 30),
            Location = new Point(0, 15)
        };
        _levelPanel.Controls.Add(panelTitle);

        var levelGroupPanel = new Panel { Location = new Point(0, 50), Size = new Size(PanelWidth, 5 * 32) };
        _levelPanel.Controls.Add(levelGroupPanel);

        _levelButtons = new RadioButton[5];
        for (int i = 0; i < 5; i++)
        {
            int level = i + 1;
            _levelButtons[i] = new RadioButton
            {
                Text = $"{level}. {LevelNames[i]}",
                ForeColor = Color.White,
                Font = new Font("Arial", 9),
                Location = new Point(10, i * 32),
                Size = new Size(PanelWidth - 20, 26),
                Checked = (level == 1),
                TabStop = false
            };
            _levelButtons[i].CheckedChanged += (s, e) =>
            {
                if (((RadioButton)s!).Checked)
                    _selectedLevel = level;
            };
            levelGroupPanel.Controls.Add(_levelButtons[i]);
        }

        // Separator line between levels and board sizes
        int separatorY = 50 + 5 * 32 + 5;
        var separator = new Panel
        {
            Location = new Point(10, separatorY),
            Size = new Size(PanelWidth - 20, 1),
            BackColor = Color.FromArgb(80, 80, 80)
        };
        _levelPanel.Controls.Add(separator);

        // Board size section title
        var sizeTitle = new Label
        {
            Text = "📐 SIZE",
            ForeColor = Color.White,
            Font = new Font("Arial", 10, FontStyle.Bold),
            AutoSize = false,
            TextAlign = ContentAlignment.MiddleCenter,
            Size = new Size(PanelWidth, 30),
            Location = new Point(0, separatorY + 8)
        };
        _levelPanel.Controls.Add(sizeTitle);

        var sizeGroupPanel = new Panel { Location = new Point(0, separatorY + 40), Size = new Size(PanelWidth, BoardSizes.Length * 32) };
        _levelPanel.Controls.Add(sizeGroupPanel);

        // Board size radio buttons
        _sizeButtons = new RadioButton[BoardSizes.Length];
        for (int i = 0; i < BoardSizes.Length; i++)
        {
            int idx = i;
            var (name, w, h) = BoardSizes[i];
            _sizeButtons[i] = new RadioButton
            {
                Text = $"{name} ({w}×{h})",
                ForeColor = Color.White,
                Font = new Font("Arial", 9),
                Location = new Point(10, i * 32),
                Size = new Size(PanelWidth - 20, 26),
                Checked = (i == _selectedSizeIndex),
                TabStop = false
            };
            _sizeButtons[i].CheckedChanged += (s, e) =>
            {
                if (((RadioButton)s!).Checked)
                {
                    _selectedSizeIndex = idx;
                    // Apply size change immediately if game is not running
                    if (_snake == null || _isGameOver)
                    {
                        ApplyBoardSize(idx);
                    }
                }
            };
            sizeGroupPanel.Controls.Add(_sizeButtons[i]);
        }

        // Keyboard shortcuts hint
        int hintsY = separatorY + 40 + BoardSizes.Length * 32 + 10;
        var hintsLabel = new Label
        {
            Text = "⌨ Shortcuts:\n\nSpace = Pause\nR = Restart\n\n← ↑ ↓ → = Move",
            ForeColor = Color.FromArgb(160, 160, 160),
            Font = new Font("Arial", 8),
            Location = new Point(10, hintsY),
            Size = new Size(PanelWidth - 20, 120)
        };
        _levelPanel.Controls.Add(hintsLabel);

        this.Controls.Add(_levelPanel);

        // Vertical separator
        this.Controls.Add(new Panel
        {
            Location = new Point(PanelWidth, 0),
            Size = new Size(2, this.ClientSize.Height),
            BackColor = Color.Gray,
            Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left
        });

        // Position everything
        LayoutControls();

        // Handle resize
        this.Resize += (s, e) => OnFormResized();
    }

    private static int ComputeGridSize(int availableWidth, int availableHeight, int cellsW, int cellsH)
    {
        int gw = (availableWidth - PanelWidth - 40) / cellsW;
        int gh = (availableHeight - 80) / cellsH;
        return Math.Max(MinGridSize, Math.Min(Math.Min(gw, gh), MaxGridSize));
    }

    private void ApplyBoardSize(int sizeIndex)
    {
        var (_, w, h) = BoardSizes[sizeIndex];
        _width = w;
        _height = h;

        // Recalculate grid size for new board dimensions
        _gridSize = ComputeGridSize(this.ClientSize.Width, this.ClientSize.Height, _width, _height);

        LayoutControls();
        _gameCanvas.Invalidate();
    }

    private void OnFormResized()
    {
        if (this.WindowState == FormWindowState.Minimized) return;

        // Recalculate grid size to fit
        _gridSize = ComputeGridSize(this.ClientSize.Width, this.ClientSize.Height, _width, _height);
        LayoutControls();
        _gameCanvas.Invalidate();
    }

    private void LayoutControls()
    {
        int canvasW = _width * _gridSize;
        int canvasH = _height * _gridSize;

        // Center the canvas in the available area (right of panel)
        int availableW = this.ClientSize.Width - PanelWidth - 20;
        int availableH = this.ClientSize.Height - 50;
        int canvasX = PanelWidth + 10 + Math.Max(0, (availableW - canvasW) / 2);
        int canvasY = 50 + Math.Max(0, (availableH - canvasH) / 2);

        _gameCanvas.Location = new Point(canvasX, canvasY);
        _gameCanvas.Size = new Size(canvasW, canvasH);

        // Score label
        _lblScore.Location = new Point(PanelWidth + 10, 12);

        // Buttons (top-right)
        _btnStart.Location = new Point(this.ClientSize.Width - _btnStart.Width - 10, 10);
        _btnPause.Location = new Point(_btnStart.Left - _btnPause.Width - 5, 10);

        // Left panel height
        _levelPanel.Size = new Size(PanelWidth, this.ClientSize.Height);
    }

    private void SetLevelButtonsEnabled(bool enabled)
    {
        foreach (var rb in _levelButtons)
        {
            rb.Enabled = enabled;
            // Fix visibility: use a dimmed color when disabled, never invisible text
            rb.ForeColor = enabled ? Color.White : Color.FromArgb(90, 90, 90);
        }
    }

    private void SetSizeButtonsEnabled(bool enabled)
    {
        foreach (var rb in _sizeButtons)
        {
            rb.Enabled = enabled;
            rb.ForeColor = enabled ? Color.White : Color.FromArgb(90, 90, 90);
        }
    }

    private void StartGame()
    {
        // If a different board size was selected during pause, apply it now
        ApplyBoardSize(_selectedSizeIndex);

        _snake = new List<Point>();
        // Start in the middle
        int startX = _width / 2;
        int startY = _height / 2;
        _snake.Add(new Point(startX, startY));
        _snake.Add(new Point(startX, startY + 1));
        _snake.Add(new Point(startX, startY + 2));

        _currentDirection = Direction.Up;
        _score = 0;
        _pendingGrowth = 0;
        _isGameOver = false;
        _isPaused = false;
        _lblScore.Text = $"Score: {_score}";
        _btnStart.Text = "\uD83D\uDD04 Restart";
        _btnStart.Enabled = true;
        _btnPause.Text = "⏸ Pause";
        _btnPause.Visible = true;
        SetLevelButtonsEnabled(false);
        SetSizeButtonsEnabled(false);

        _gameTimer.Interval = GetLevelSpeed(_selectedLevel);

        GenerateObstacles();
        _foods.Clear();
        SpawnFood();
        SpawnFood();
        _gameTimer.Start();
        _gameCanvas.Invalidate();
        // Focus form to ensure key events are captured if anything else had focus
        this.Focus(); 
    }

    // ... (SpawnFood, GameTimer_Tick, MoveSnake, CheckCollision, GameOver unchanged)

    private void SpawnFood()
    {
        FoodType type = FoodType.Green;
        int increase = 2;
        int size = 1;
        
        int roll = _random.Next(100);
        if (roll < 3) { type = FoodType.MegaGold; size = 2; increase = 24; }
        else if (roll < 10) { type = FoodType.MegaRed; size = 2; increase = 16; }
        else if (roll < 20) { type = FoodType.MegaGreen; size = 2; increase = 8; }
        else if (roll < 30) { type = FoodType.Blue; size = 1; increase = _random.Next(2, 25); } // random from 2 to 24
        else if (roll < 45) { type = FoodType.Gold; size = 1; increase = 6; }
        else if (roll < 70) { type = FoodType.Red; size = 1; increase = 4; }
        else { type = FoodType.Green; size = 1; increase = 2; }

        while (true)
        {
            // Avoid spawning food on border cells
            int x = _random.Next(1, _width - size);
            int y = _random.Next(1, _height - size);
            
            bool valid = true;
            for(int i = 0; i < size; i++)
            {
                for(int j = 0; j < size; j++)
                {
                    Point pt = new Point(x + i, y + j);
                    if (_snake.Contains(pt) || (_obstacles != null && _obstacles.Contains(pt)) || _foods.Any(f => pt.X >= f.Position.X && pt.X < f.Position.X + f.Size && pt.Y >= f.Position.Y && pt.Y < f.Position.Y + f.Size))
                    {
                        valid = false;
                        break;
                    }
                }
                if (!valid) break;
            }

            if (valid)
            {
                _foods.Add(new FoodItem { Position = new Point(x, y), Type = type, Size = size, IncreaseAmount = increase });
                break;
            }
        }
    }

    private void GenerateObstacles()
    {
        _obstacles = new List<Point>();
        int numberOfObstacles = GetLevelObstacleCount(_selectedLevel);

        for (int i = 0; i < numberOfObstacles; i++)
        {
            // Avoid spawning obstacles on border cells
            int x = _random.Next(1, _width - 1);
            int y = _random.Next(1, _height - 1);
            Point obsPoint = new Point(x, y);

            // Don't spawn obstacle on snake, or where one already exists
            if (!_snake.Contains(obsPoint) && !_obstacles.Contains(obsPoint))
            {
                _obstacles.Add(obsPoint);
            }
        }
    }

    private void GameTimer_Tick(object sender, EventArgs e)
    {
        if (_isGameOver || _isPaused) return;

        MoveSnake();
        CheckCollision();
        _gameCanvas.Invalidate(); // Trigger redraw
    }

    private void MoveSnake()
    {
        Point head = _snake[0];
        Point newHead = head;

        switch (_currentDirection)
        {
            case Direction.Up:
                newHead.Y--;
                break;
            case Direction.Down:
                newHead.Y++;
                break;
            case Direction.Left:
                newHead.X--;
                break;
            case Direction.Right:
                newHead.X++;
                break;
        }

        _snake.Insert(0, newHead);

        // Check if ate food
        FoodItem? eaten = null;
        foreach (var f in _foods)
        {
            if (newHead.X >= f.Position.X && newHead.X < f.Position.X + f.Size &&
                newHead.Y >= f.Position.Y && newHead.Y < f.Position.Y + f.Size)
            {
                eaten = f;
                break;
            }
        }

        if (eaten != null)
        {
            int growth = eaten.IncreaseAmount;
            int scoreAdd = growth * 10; // Score scales with the length increase

            _score += scoreAdd;
            _lblScore.Text = $"Score: {_score}";
            _foods.Remove(eaten);
            _pendingGrowth += (growth - 1);
            SpawnFood();
        }
        else
        {
            if (_pendingGrowth > 0)
            {
                _pendingGrowth--;
            }
            else
            {
                _snake.RemoveAt(_snake.Count - 1); // Remove tail to maintain length
            }
        }
    }

    private void CheckCollision()
    {
        Point head = _snake[0];

        // Wall Collision
        if (head.X < 0 || head.X >= _width || head.Y < 0 || head.Y >= _height)
        {
            GameOver();
            return;
        }

        // Self Collision (start from index 1 because index 0 is head)
        for (int i = 1; i < _snake.Count; i++)
        {
            if (head == _snake[i])
            {
                GameOver();
                return;
            }
        }

        // Obstacle Collision
        if (_obstacles != null && _obstacles.Contains(head))
        {
            GameOver();
            return;
        }
    }

    private void GameOver()
    {
        _isGameOver = true;
        _gameTimer.Stop();
        _btnStart.Text = "\uD83C\uDFAE New Game";
        _btnStart.Enabled = true;
        _btnPause.Visible = false;
        SetLevelButtonsEnabled(true);
        SetSizeButtonsEnabled(true);
        MessageBox.Show($"Game Over! Final Score: {_score}", "Game Over", MessageBoxButtons.OK, MessageBoxIcon.Information);
    }

    // Override ProcessCmdKey to intercept arrow keys before WinForms
    // consumes them for control navigation
    protected override bool ProcessCmdKey(ref Message msg, Keys keyData)
    {
        // Pause toggle (Space or P)
        if (keyData == Keys.Space || keyData == Keys.P)
        {
            if (!_isGameOver && _snake != null)
                TogglePause();
            return true;
        }

        // Restart (R)
        if (keyData == Keys.R)
        {
            if (_snake != null)
                StartGame();
            return true;
        }

        if (_isGameOver || _isPaused)
            return base.ProcessCmdKey(ref msg, keyData);

        // Prevent 180 degree turns
        switch (keyData)
        {
            case Keys.Up:
                if (_currentDirection != Direction.Down) _currentDirection = Direction.Up;
                return true;
            case Keys.Down:
                if (_currentDirection != Direction.Up) _currentDirection = Direction.Down;
                return true;
            case Keys.Left:
                if (_currentDirection != Direction.Right) _currentDirection = Direction.Left;
                return true;
            case Keys.Right:
                if (_currentDirection != Direction.Left) _currentDirection = Direction.Right;
                return true;
        }

        return base.ProcessCmdKey(ref msg, keyData);
    }

    private void TogglePause()
    {
        if (_isGameOver) return;

        _isPaused = !_isPaused;
        if (_isPaused)
        {
            _gameTimer.Stop();
            _btnPause.Text = "▶ Resume";
            // Allow changing level and size during pause (will apply on restart)
            SetLevelButtonsEnabled(true);
            SetSizeButtonsEnabled(true);
        }
        else
        {
            _gameTimer.Start();
            _btnPause.Text = "⏸ Pause";
            SetLevelButtonsEnabled(false);
            SetSizeButtonsEnabled(false);
            this.Focus();
        }
        _gameCanvas.Invalidate();
    }

    private void GameCanvas_Paint(object? sender, PaintEventArgs e)
    {
        Graphics g = e.Graphics;

        if (_snake == null) return; // Game hasn't started yet

        // Draw Foods
        foreach (var f in _foods)
        {
            Brush fillBrush = Brushes.LimeGreen;
            Pen outlinePen = Pens.DarkGreen;
            
            if (f.Type == FoodType.Green || f.Type == FoodType.MegaGreen) { fillBrush = Brushes.LimeGreen; outlinePen = Pens.DarkGreen; }
            else if (f.Type == FoodType.Red || f.Type == FoodType.MegaRed) { fillBrush = Brushes.Red; outlinePen = Pens.DarkRed; }
            else if (f.Type == FoodType.Gold || f.Type == FoodType.MegaGold) { fillBrush = Brushes.Gold; outlinePen = Pens.DarkGoldenrod; }
            else if (f.Type == FoodType.Blue) { fillBrush = Brushes.DeepSkyBlue; outlinePen = Pens.DarkBlue; }

            if (f.Type == FoodType.Blue)
            {
                // Draw star shape
                int cx = f.Position.X * _gridSize + _gridSize / 2;
                int cy = f.Position.Y * _gridSize + _gridSize / 2;
                int outerRadius = _gridSize / 2;
                int innerRadius = _gridSize / 4;
                
                PointF[] starPoints = new PointF[10];
                for (int i = 0; i < 10; i++)
                {
                    double angle = i * Math.PI / 5 - Math.PI / 2;
                    double radius = (i % 2 == 0) ? outerRadius : innerRadius;
                    starPoints[i] = new PointF((float)(cx + Math.Cos(angle) * radius), (float)(cy + Math.Sin(angle) * radius));
                }
                g.FillPolygon(fillBrush, starPoints);
                g.DrawPolygon(outlinePen, starPoints);
            }
            else
            {
                g.FillEllipse(fillBrush, f.Position.X * _gridSize, f.Position.Y * _gridSize, _gridSize * f.Size, _gridSize * f.Size);
                g.DrawEllipse(outlinePen, f.Position.X * _gridSize, f.Position.Y * _gridSize, _gridSize * f.Size, _gridSize * f.Size);
            }
        }

        // Draw Obstacles
        if (_obstacles != null)
        {
            foreach (var obs in _obstacles)
            {
                g.FillRectangle(Brushes.Gray, obs.X * _gridSize, obs.Y * _gridSize, _gridSize, _gridSize);
                g.DrawRectangle(Pens.Black, obs.X * _gridSize, obs.Y * _gridSize, _gridSize, _gridSize);
            }
        }

        // Draw Snake
        for (int i = 0; i < _snake.Count; i++)
        {
            Brush snakeColor = (i == 0) ? Brushes.Chartreuse : Brushes.Green; // Head is different color
            g.FillRectangle(snakeColor, _snake[i].X * _gridSize, _snake[i].Y * _gridSize, _gridSize, _gridSize);
            g.DrawRectangle(Pens.Black, _snake[i].X * _gridSize, _snake[i].Y * _gridSize, _gridSize, _gridSize); // Outline
        }

        // Draw Pause overlay
        if (_isPaused)
        {
            using var overlay = new SolidBrush(Color.FromArgb(150, 0, 0, 0));
            g.FillRectangle(overlay, 0, 0, _gameCanvas.Width, _gameCanvas.Height);

            string pauseText = "⏸ PAUSED";
            using var pauseFont = new Font("Arial", 28, FontStyle.Bold);
            var textSize = g.MeasureString(pauseText, pauseFont);
            float tx = (_gameCanvas.Width - textSize.Width) / 2;
            float ty = (_gameCanvas.Height - textSize.Height) / 2 - 20;
            g.DrawString(pauseText, pauseFont, Brushes.White, tx, ty);

            string hintText = "Press Space to resume";
            using var hintFont = new Font("Arial", 12);
            var hintSize = g.MeasureString(hintText, hintFont);
            float hx = (_gameCanvas.Width - hintSize.Width) / 2;
            float hy = ty + textSize.Height + 10;
            g.DrawString(hintText, hintFont, Brushes.LightGray, hx, hy);
        }
    }

    private enum FoodType
    {
        Green,
        Red,
        Gold,
        Blue,
        MegaGreen,
        MegaRed,
        MegaGold
    }

    private class FoodItem
    {
        public Point Position { get; set; }
        public FoodType Type { get; set; }
        public int Size { get; set; } = 1;
        public int IncreaseAmount { get; set; }
    }
}
