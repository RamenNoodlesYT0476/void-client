using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Windows.Forms;

partial class Desktop {
    readonly Button signIn = new Button();
    readonly Label selectedTitle = new Label();
    bool fullScreen;
    Rectangle windowedBounds;
    FormWindowState previousState;

    static void StyleDialog(Form f) {
        f.BackColor=ColorOf("#181720"); f.ForeColor=Color.White;
        foreach(Control c in f.Controls) {
            if(c is Button) { ((Button)c).FlatStyle=FlatStyle.Flat; c.BackColor=ColorOf("#40344e"); }
            if(c is TextBox || c is NumericUpDown) { c.BackColor=ColorOf("#292834"); c.ForeColor=Color.White; }
        }
    }
    Button Command(string caption, Action run,string color,Button existing=null) {
        var b=existing??new Button();
        b.Text=caption;b.AutoSize=true;b.AutoSizeMode=AutoSizeMode.GrowAndShrink;
        b.Padding=new Padding(8,4,8,4);b.Margin=new Padding(0,2,6,2);
        b.ForeColor=Color.White;b.BackColor=ColorOf(color);b.FlatStyle=FlatStyle.Flat;
        b.FlatAppearance.BorderSize=0;b.Cursor=Cursors.Hand;
        b.Click+=delegate{if(!working)try{run();}catch(Exception e){status.Text=e.Message;}};
        return b;
    }
    static FlowLayoutPanel Bar(params Control[] buttons) {
        var bar=new FlowLayoutPanel{AutoSize=true,Dock=DockStyle.Fill,WrapContents=true,Margin=Padding.Empty};
        bar.Controls.AddRange(buttons);return bar;
    }
    static Label Heading(string text,int size,string color) {
        return new Label{Text=text,AutoSize=true,Font=new Font("Segoe UI",size,FontStyle.Bold),
            ForeColor=ColorOf(color),Margin=new Padding(0,4,0,8)};
    }
    static TableLayoutPanel Column() {
        return new TableLayoutPanel{Dock=DockStyle.Fill,ColumnCount=1,AutoSize=false,Margin=Padding.Empty};
    }
    static void Row(TableLayoutPanel panel, Control item,bool expand=false) {
        int row=panel.RowStyles.Count;
        panel.RowCount=row+1;
        panel.RowStyles.Add(expand?new RowStyle(SizeType.Percent,100):new RowStyle(SizeType.AutoSize));
        panel.Controls.Add(item,0,row);
    }
    static void StyleList(ListBox list) {
        list.Dock=DockStyle.Fill;list.IntegralHeight=false;list.BorderStyle=BorderStyle.None;
        list.BackColor=ColorOf("#22212b");list.ForeColor=ColorOf("#eeeaf5");
        list.HorizontalScrollbar=true;list.Margin=new Padding(0,0,0,10);
    }
    void BuildLibrary() {
        AutoScaleMode=AutoScaleMode.Dpi;FormBorderStyle=FormBorderStyle.Sizable;MaximizeBox=true;
        MinimumSize=new Size(760,490);ClientSize=new Size(940,595);
        var outer=Column();outer.Padding=new Padding(18);Controls.Add(outer);
        var header=new TableLayoutPanel{Dock=DockStyle.Fill,AutoSize=true,ColumnCount=2,Margin=new Padding(0,0,0,12)};
        header.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));header.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
        header.Controls.Add(Heading("Void Launcher",23,"#ff476a"),0,0);
        header.Controls.Add(Bar(Command("Settings",Settings,"#363440"),Command("Fullscreen · F11",ToggleFullScreen,"#363440")),1,0);
        Row(outer,header);
        var panes=new TableLayoutPanel{Dock=DockStyle.Fill,ColumnCount=2,Margin=Padding.Empty};
        panes.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,34));panes.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,66));Row(outer,panes,true);
        var library=Column();library.BackColor=ColorOf("#1b1922");library.Padding=new Padding(12);library.Margin=new Padding(0,0,10,0);
        var detail=Column();detail.BackColor=ColorOf("#1b1922");detail.Padding=new Padding(12);
        panes.Controls.Add(library,0,0);panes.Controls.Add(detail,1,0);
        Row(library,Heading("Instances",14,"#c49aff"));StyleList(instances);Row(library,instances,true);
        Row(library,Bar(Command("New instance",NewInstance,"#5a4078"),Command("Open folder",delegate{
            if(Active!=null)Process.Start("explorer.exe","\""+Active+"\"");},"#383640")));
        selectedTitle.Font=new Font("Segoe UI",15,FontStyle.Bold);selectedTitle.AutoEllipsis=true;selectedTitle.Dock=DockStyle.Fill;selectedTitle.Height=32;selectedTitle.Text="Select an instance";
        Row(detail,selectedTitle);Row(detail,Heading("Installed mods   ·   Fabric 1.21.11",10,"#8bbafa"));
        StyleList(mods);Row(detail,mods,true);
        Row(detail,Bar(Command("Browse mods",delegate{if(Active==null)return;using(var browser=new ModBrowser(Active,instances.SelectedItem.ToString()))browser.ShowDialog(this);RefreshMods();},"#344e78"),
            Command("Add JARs",ImportMods,"#383640"),Command("Enable / disable",ToggleMod,"#383640"),
            Command("Install Void utilities",InstallUtilities,"#5a4078")));
        instances.SelectedIndexChanged+=delegate{
            selectedTitle.Text=Active==null?"Select an instance":instances.SelectedItem.ToString();RefreshMods();
            if(Active!=null && prefs.Remember){prefs.Last=Path.GetFileName(Active);SavePrefs();}
        };
        var footer=new TableLayoutPanel{ColumnCount=3,AutoSize=true,Dock=DockStyle.Fill,Margin=new Padding(0,12,0,0)};
        footer.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));footer.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));footer.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
        footer.Controls.Add(Command("Sign in with Microsoft",AccountClick,"#344e78",signIn),0,0);
        account.Dock=DockStyle.Fill;account.TextAlign=ContentAlignment.MiddleLeft;account.AutoEllipsis=true;footer.Controls.Add(account,1,0);
        footer.Controls.Add(Bar(Command("Launch with Prism",delegate{RunWorker("prism");},"#5a4078"),
            Command("Play instance",delegate{RunWorker("play");},"#ac254c")),2,0);
        Row(outer,footer);status.Dock=DockStyle.Fill;status.MinimumSize=new Size(0,38);status.ForeColor=ColorOf("#b7b4c7");Row(outer,status);
    }
    void ToggleFullScreen(){
        if(!fullScreen){
            previousState=WindowState;windowedBounds=WindowState==FormWindowState.Normal?Bounds:RestoreBounds;
            var monitor=Screen.FromControl(this);WindowState=FormWindowState.Normal;
            FormBorderStyle=FormBorderStyle.None;Bounds=monitor.Bounds;fullScreen=true;
        }else{
            FormBorderStyle=FormBorderStyle.Sizable;Bounds=windowedBounds;WindowState=previousState;fullScreen=false;
        }
    }
    protected override bool ProcessCmdKey(ref Message msg,Keys key){
        if(!setup && (key==Keys.F11 || (fullScreen && key==Keys.Escape))){ToggleFullScreen();return true;}
        return base.ProcessCmdKey(ref msg,key);
    }
}
