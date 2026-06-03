namespace SnakeGame;

partial class SnakeForm
{
    /// <summary>
    ///  Required designer variable.
    /// </summary>
    private System.ComponentModel.IContainer components = null;

    /// <summary>
    ///  Clean up any resources being used.
    /// </summary>
    /// <param name="disposing">true if managed resources should be disposed; otherwise, false.</param>
    protected override void Dispose(bool disposing)
    {
        if (disposing && (components != null))
        {
            components.Dispose();
        }
        base.Dispose(disposing);
    }

    #region Windows Form Designer generated code

    /// <summary>
    ///  Required method for Designer support - do not modify
    ///  the contents of this method with the code editor.
    /// </summary>
    private void InitializeComponent()
    {
        components = new System.ComponentModel.Container();
        AutoScaleMode = AutoScaleMode.Font;

        // Set size to at least half the screen
        var screen = Screen.PrimaryScreen.WorkingArea;
        ClientSize = new Size(
            Math.Max(screen.Width / 2, 800),
            Math.Max(screen.Height / 2, 450));

        StartPosition = FormStartPosition.CenterScreen;
        Text = "Form1";
    }

    #endregion
}
