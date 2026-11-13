using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;

sealed class ModBrowser : Form {
    readonly string instance;
    readonly ComboBox provider = new ComboBox();
    readonly TextBox query = new TextBox();
    readonly ListBox hits = new ListBox();
    readonly Label status = new Label();
    readonly Button previous = new Button(), next = new Button();
    readonly JavaScriptSerializer json = new JavaScriptSerializer();
    int offset;

    public ModBrowser(string directory,string instanceName) {
        Icon = System.Drawing.Icon.ExtractAssociatedIcon(Application.ExecutablePath);
        instance=directory; Text="Browse mods • "+instanceName;
        ClientSize=new Size(572,430);StartPosition=FormStartPosition.CenterParent;
        MinimumSize=Size;FormBorderStyle=FormBorderStyle.Sizable;MaximizeBox=true;
        BackColor=Color.FromArgb(24,22,32);ForeColor=Color.White;
        provider.DropDownStyle=ComboBoxStyle.DropDownList;
        provider.Items.AddRange(new[]{"Modrinth","CurseForge"});provider.SelectedIndex=0;
        provider.SetBounds(14,14,128,28);query.SetBounds(150,14,292,28);
        Controls.AddRange(new Control[]{provider,query,hits,status});
        AddButton(new Button(),"Search",450,12,108,delegate{offset=0;Search();});
        hits.SetBounds(14,55,544,244);hits.DisplayMember="Name";
        hits.BackColor=Color.FromArgb(35,32,45);hits.ForeColor=Color.White;
        AddButton(previous,"Previous",14,311,90,delegate{offset-=20;Search();});
        AddButton(next,"Next",110,311,80,delegate{offset+=20;Search();});
        AddButton(new Button(),"Website",217,311,95,OpenSite);
        AddButton(new Button(),"Review and install",325,311,233,Install);
        status.SetBounds(14,357,540,65);
        hits.Anchor=AnchorStyles.Top|AnchorStyles.Left|AnchorStyles.Right|AnchorStyles.Bottom;
        query.Anchor=AnchorStyles.Top|AnchorStyles.Left|AnchorStyles.Right;
        status.Anchor=AnchorStyles.Bottom|AnchorStyles.Left|AnchorStyles.Right;
        foreach(Control control in Controls)if(control is Button)
            control.Anchor=control.Top<50?AnchorStyles.Top|AnchorStyles.Right:AnchorStyles.Bottom|AnchorStyles.Left;
        ResetResults();
        provider.SelectedIndexChanged+=delegate{ResetResults();};
        query.TextChanged+=delegate{ResetResults();};
        query.KeyDown+=delegate(object sender,KeyEventArgs e){if(e.KeyCode==Keys.Enter){e.SuppressKeyPress=true;offset=0;Search();}};
        Shown+=delegate{Search();};
    }
    void AddButton(Button button,string label,int x,int y,int width,Action action){
        button.Text=label;button.SetBounds(x,y,width,30);button.FlatStyle=FlatStyle.Flat;
        button.BackColor=Color.FromArgb(78,55,96);Controls.Add(button);button.Click+=delegate{action();};
    }
    sealed class Hit {public string Id; public string Name{get;set;}}
    void ResetResults(){offset=0;hits.Items.Clear();previous.Enabled=next.Enabled=false;status.Text="Search Fabric mods for Minecraft 1.21.11.";}
    void OpenSite(){
        string q=Uri.EscapeDataString(query.Text);
        Process.Start(provider.SelectedIndex==0?"https://modrinth.com/mods?v=1.21.11&g=categories:fabric&q="+q:"https://www.curseforge.com/minecraft/search?class=mc-mods&search="+q);
    }
    async Task<object> Call(string action,Dictionary<string,object> args){
        args["action"]=action;args["instance"]=instance;args["provider"]=provider.Text;
        string engine=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"engine");
        var info=new ProcessStartInfo(Path.Combine(engine,"python.exe"),"-B catalog.py"){
            WorkingDirectory=engine,UseShellExecute=false,CreateNoWindow=true,RedirectStandardInput=true,RedirectStandardOutput=true};
        using(var worker=Process.Start(info)){
            worker.StandardInput.Write(json.Serialize(args));worker.StandardInput.Close();
            var reply=json.Deserialize<Dictionary<string,object>>(await worker.StandardOutput.ReadToEndAsync());
            if(reply==null)throw new IOException("Catalog worker closed.");
            if(reply.ContainsKey("error"))throw new IOException(reply["error"].ToString());
            return reply["result"];
        }
    }
    async void Search(){
        Enabled=false;hits.Items.Clear();status.Text="Searching…";
        try{
            var rows=(IEnumerable)await Call("search",new Dictionary<string,object>{{"query",query.Text},{"offset",offset}});
            foreach(Dictionary<string,object> row in rows)hits.Items.Add(new Hit{Id=(string)row["id"],Name=(string)row["name"]});
            next.Enabled=hits.Items.Count==20;
            status.Text=hits.Items.Count==0?"No matching projects.":String.Format("Results {0}–{1}",offset+1,offset+hits.Items.Count);
        }catch(Exception e){status.Text=e.Message;next.Enabled=false;}finally{previous.Enabled=offset>0;Enabled=true;}
    }
    async void Install(){
        var hit=hits.SelectedItem as Hit;if(hit==null){status.Text="Select a project first.";return;}
        Enabled=false;status.Text="Finding compatible release…";
        try{
            var args=new Dictionary<string,object>{{"id",hit.Id}};
            var file=(Dictionary<string,object>)await Call("release",args);
            string prompt=String.Format("Install {0} into {1}?\n\nRequired dependencies to check separately: {2}",file["filename"],Text.Substring("Browse mods • ".Length),file["dependencies"]);
            if(MessageBox.Show(this,prompt,"Install mod",MessageBoxButtons.OKCancel)!=DialogResult.OK){status.Text="Installation cancelled.";return;}
            args["filename"]=file["filename"];args["digest"]=file["digest"];status.Text="Downloading…";
            await Call("install",args);status.Text="Installed "+file["filename"]+".";
        }catch(Exception e){status.Text=e.Message;}finally{Enabled=true;}
    }
}
