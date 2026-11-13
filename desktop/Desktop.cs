using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;

partial class Desktop : Form {
    static readonly string HomeDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Void Client");
    static readonly string Instances = Path.Combine(HomeDir,"instances");
    static readonly string Source = AppDomain.CurrentDomain.BaseDirectory;
    static readonly string SessionFile = Path.Combine(HomeDir,"account.dat");
    static readonly string ClientFile = Path.Combine(HomeDir,"microsoft-app.txt");
    static readonly string[] Core = {"void-client-1.1.0.jar","fabric-api-0.141.6+1.21.11.jar"};
    static readonly JavaScriptSerializer Json = new JavaScriptSerializer();
    readonly ListBox instances = new ListBox(), mods = new ListBox();
    readonly Label status = new Label(), account = new Label();
    readonly bool setup;
    public class Preferences {
        public int MemoryMiB { get; set; }
        public bool Remember { get; set; }
        public string Last { get; set; }
        public string PrismExe { get; set; }
        public string PrismRoot { get; set; }
        public Preferences(){ MemoryMiB=3072; Remember=true; }
    }
    Preferences prefs = new Preferences();
    static readonly string PrefFile=Path.Combine(HomeDir,"launcher-settings.json");
    void SavePrefs(){
        string tmp=PrefFile+".tmp";
        File.WriteAllText(tmp,Json.Serialize(prefs));
        if(File.Exists(PrefFile))File.Replace(tmp,PrefFile,null);else File.Move(tmp,PrefFile);
    }
    bool working;
    string Active { get { var row = instances.SelectedItem as Instance; return row == null ? null : row.PathName; } }
    class Instance {
        public string Name, PathName;
        public override string ToString(){return Name;}
    }
    static Color ColorOf(string value){return ColorTranslator.FromHtml(value);}
    void LabelAt(Label label,string text,int x,int y,int w,int h,float size,string color){
        label.SetBounds(x,y,w,h);label.Text=text;label.Font=new Font("Segoe UI",size);
        label.ForeColor=ColorOf(color);Controls.Add(label);
    }
    void ButtonAt(string text,int x,int y,int w,string color,Action run){
        var b=new Button{Text=text,FlatStyle=FlatStyle.Flat,BackColor=ColorOf(color),ForeColor=Color.White};
        b.FlatAppearance.BorderSize=0;b.SetBounds(x,y,w,35);Controls.Add(b);
        b.Click+=delegate{if(working)return;try{run();}catch(Exception e){status.Text=e.Message;}};
    }
    void ListAt(ListBox list,int x,int width){
        list.SetBounds(x,125,width,195);list.BackColor=ColorOf("#1d1b26");list.ForeColor=Color.White;
        list.BorderStyle=BorderStyle.FixedSingle;list.IntegralHeight=false;Controls.Add(list);
    }
    Desktop(bool install){
        setup=install;Text=install?"Void Launcher Setup":"Void Launcher";ClientSize=new Size(704,install?234:472);
        Font=new Font("Segoe UI",10);BackColor=ColorOf("#121118");ForeColor=Color.White;
        FormBorderStyle=FormBorderStyle.FixedDialog;MaximizeBox=false;StartPosition=FormStartPosition.CenterScreen;
        LabelAt(new Label(),install?"Install Void Launcher":"Void Launcher",20,12,350,46,23,"#ff476d");
        LabelAt(new Label(),"Minecraft Java 1.21.11   /   Fabric",22,60,310,26,10,"#b8a4d5");
        if(install){
            LabelAt(status,"Install Void Launcher, then create instances and choose their mods.",22,100,660,56,11,"#ccd0dd");
            ButtonAt("Install and open launcher",22,169,238,"#9e2549",Install);
        }else{
            Controls.Clear();
            BuildLibrary();
            Shown+=delegate{try{Directory.CreateDirectory(Instances);var original=Path.Combine(Instances,"1.21.11");
                if(File.Exists(PrefFile))prefs=Json.Deserialize<Preferences>(File.ReadAllText(PrefFile))??new Preferences();
                Reload(prefs.Remember && !String.IsNullOrEmpty(prefs.Last)?Path.Combine(Instances,Path.GetFileName(prefs.Last)):original);
                if(instances.SelectedIndex<0 && instances.Items.Count>0)instances.SelectedIndex=0;
                if(instances.Items.Count==0)status.Text="Create your first instance to add mods and play.";
                ShowAccount();
            }catch(Exception e){status.Text=e.Message;}};
        }
    }
    static void CopyTree(string src,string dst){
        Directory.CreateDirectory(dst);
        foreach(var f in Directory.GetFiles(src))File.Copy(f,Path.Combine(dst,Path.GetFileName(f)),true);
        foreach(var d in Directory.GetDirectories(src))CopyTree(d,Path.Combine(dst,Path.GetFileName(d)));
    }
    async void Install(){
        working=true;status.Text="Installing launcher files…";
        try{await Task.Run(delegate{
            Directory.CreateDirectory(HomeDir);
            foreach(var d in new[]{"engine","payload"})CopyTree(Path.Combine(Source,d),Path.Combine(HomeDir,d));
            File.Copy(Path.Combine(Source,"Void Launcher.exe"),Path.Combine(HomeDir,"Void Launcher.exe"),true);
            var publisherId=Path.Combine(Source,"microsoft-app.txt");Guid publicId;
            if(File.Exists(publisherId) && Guid.TryParse(File.ReadAllText(publisherId).Trim(),out publicId))File.WriteAllText(ClientFile,publicId.ToString());
            var start=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs),"Void Client");
            Directory.CreateDirectory(start);File.WriteAllText(Path.Combine(start,"Void Launcher.url"),"[InternetShortcut]\r\nURL="+new Uri(Path.Combine(HomeDir,"Void Launcher.exe")).AbsoluteUri+"\r\n");
        });Process.Start(Path.Combine(HomeDir,"Void Launcher.exe"));Close();}
        catch(Exception e){status.Text=e.Message;}finally{working=false;}
    }
    public static void InstallUtilityPack(string dir){
        var folder=Path.Combine(dir,"mods");Directory.CreateDirectory(folder);
                foreach(var file in Core){
            var target=Path.Combine(folder,file);
            var staged=target+"."+Guid.NewGuid().ToString("N")+".part";
            try{
                File.Copy(Path.Combine(Source,"payload",file),staged);
                if(File.Exists(target))File.Replace(staged,target,null);else File.Move(staged,target);
                if(File.Exists(target+".disabled"))File.Delete(target+".disabled");
            }finally{if(File.Exists(staged))File.Delete(staged);}
        }
    }
    public static string CreateInstance(string root,string name){
        name=name.Trim();if(name.Length==0||name.Length>70)throw new IOException("Choose an instance name between 1 and 70 characters.");
        Directory.CreateDirectory(root);
        string id=Guid.NewGuid().ToString("N"),stage=Path.Combine(root,"."+id),dest=Path.Combine(root,id);
        Directory.CreateDirectory(stage);
        try{Directory.CreateDirectory(Path.Combine(stage,"mods"));File.WriteAllText(Path.Combine(stage,"instance.txt"),name);Directory.Move(stage,dest);}
        catch{Directory.Delete(stage,true);throw;}
        return dest;
    }
    void Reload(string select){
        instances.Items.Clear();
        foreach(var dir in Directory.GetDirectories(Instances).OrderBy(p=>p)){
            string meta=Path.Combine(dir,"instance.txt");bool original=Path.GetFileName(dir)=="1.21.11";
            if(!original&&(!File.Exists(meta)||Path.GetFileName(dir).StartsWith(".")))continue;
            var item=new Instance{Name=original?"Void Client 1.21.11":File.ReadAllText(meta),PathName=dir};
            instances.Items.Add(item);if(dir==select)instances.SelectedItem=item;
        }
    }
    static string Prompt(string title,string label,string value){
        using(var dialog=new Form{Text=title,ClientSize=new Size(460,126),StartPosition=FormStartPosition.CenterParent,FormBorderStyle=FormBorderStyle.FixedDialog}){
            dialog.Controls.Add(new Label{Text=label,Left=12,Top=10,Width=435,Height=32});
            var input=new TextBox{Left=12,Top=46,Width=435,Text=value};dialog.Controls.Add(input);
            var ok=new Button{Text="Save",Left=359,Top=84,DialogResult=DialogResult.OK};dialog.Controls.Add(ok);dialog.AcceptButton=ok;
            StyleDialog(dialog);return dialog.ShowDialog()==DialogResult.OK?input.Text.Trim():null;
        }
    }
    void InstallUtilities(){
        if(Active==null){status.Text="Select or create an instance first.";return;}
        try{InstallUtilityPack(Active);status.Text="Void utilities installed. Press Right Shift in this instance to configure them.";}
        finally{RefreshMods();}
    }
    void Settings(){
        using(var dialog=new Form{Text="Launcher settings",ClientSize=new Size(420,210),StartPosition=FormStartPosition.CenterParent,FormBorderStyle=FormBorderStyle.FixedDialog,MaximizeBox=false}){
            dialog.Controls.Add(new Label{Text="Maximum game memory (MiB)",Left=16,Top=19,Width=245});
            var ram=new NumericUpDown{Left=265,Top=16,Minimum=1024,Maximum=32768,Increment=256,Value=Math.Max(1024,Math.Min(32768,prefs.MemoryMiB))};
            var remember=new CheckBox{Text="Remember the last selected instance",Checked=prefs.Remember,Left=16,Top=59,Width=365};
            var app=new Button{Text="Microsoft application settings",Left=16,Top=103,Width=235,Height=30};
            app.Click+=delegate{try{Configure();}catch(Exception e){MessageBox.Show(dialog,e.Message);}};
            var save=new Button{Text="Save",Left=307,Top=158,DialogResult=DialogResult.OK};
            dialog.Controls.AddRange(new Control[]{ram,remember,app,save});dialog.AcceptButton=save;
            StyleDialog(dialog);if(dialog.ShowDialog(this)!=DialogResult.OK)return;
            prefs.MemoryMiB=(int)ram.Value;prefs.Remember=remember.Checked;
            prefs.Last=prefs.Remember && Active!=null?Path.GetFileName(Active):null;SavePrefs();
            status.Text="Launcher settings saved.";
        }
    }
    void NewInstance(){var name=Prompt("New instance","Name this Fabric 1.21.11 instance","");if(name!=null)Reload(CreateInstance(Instances,name));}
    void RefreshMods(){
        mods.Items.Clear();if(Active==null)return;
        var dir=Path.Combine(Active,"mods");Directory.CreateDirectory(dir);
        foreach(var path in Directory.GetFiles(dir).OrderBy(p=>p,StringComparer.OrdinalIgnoreCase))
            if(path.EndsWith(".jar",StringComparison.OrdinalIgnoreCase)||path.EndsWith(".jar.disabled",StringComparison.OrdinalIgnoreCase))mods.Items.Add(Path.GetFileName(path));
    }
    public static void AddJar(string instance,string file){
        if(!file.EndsWith(".jar",StringComparison.OrdinalIgnoreCase))throw new IOException("Choose a Fabric JAR.");
        using(var archive=ZipFile.OpenRead(file)){if(archive.GetEntry("fabric.mod.json")==null)throw new IOException("Missing Fabric metadata in "+Path.GetFileName(file));}
        string dest=Path.Combine(instance,"mods",Path.GetFileName(file));
        if(File.Exists(dest+".disabled"))throw new IOException("This mod is already installed and disabled.");
        var tmp=dest+"."+Guid.NewGuid().ToString("N")+".part";
        try{File.Copy(file,tmp);File.Move(tmp,dest);}finally{if(File.Exists(tmp))File.Delete(tmp);}
    }
    void ImportMods(){if(Active==null)return;using(var picker=new OpenFileDialog{Filter="Fabric mods (*.jar)|*.jar",Multiselect=true}){
        if(picker.ShowDialog()!=DialogResult.OK)return;try{foreach(var file in picker.FileNames)AddJar(Active,file);status.Text="Mods added to the selected instance.";}finally{RefreshMods();}}}
    void ToggleMod(){
        var name=mods.SelectedItem as string;if(name==null||Active==null)return;
        var src=Path.Combine(Active,"mods",name);File.Move(src,src.EndsWith(".disabled",StringComparison.OrdinalIgnoreCase)?src.Substring(0,src.Length-9):src+".disabled");RefreshMods();
    }
    static string ClientId(){return Environment.GetEnvironmentVariable("VOID_MICROSOFT_CLIENT_ID")??(File.Exists(ClientFile)?File.ReadAllText(ClientFile).Trim():"");}
    void Configure(){var value=Prompt("Microsoft application","Void's approved public application ID (desktop redirect http://localhost)",ClientId());if(value==null)return;
        Guid id;if(!Guid.TryParse(value,out id))throw new IOException("Enter a Microsoft application ID (GUID).");
        File.WriteAllText(ClientFile,id.ToString());ShowAccount();status.Text="Application ID saved.";}
    static Dictionary<string,object> ReadSession(){
        if(!File.Exists(SessionFile))return null;
        var raw=ProtectedData.Unprotect(File.ReadAllBytes(SessionFile),null,DataProtectionScope.CurrentUser);
        return Json.Deserialize<Dictionary<string,object>>(Encoding.UTF8.GetString(raw));
    }
    static void SaveSession(Dictionary<string,object> data){
        var bytes=ProtectedData.Protect(Encoding.UTF8.GetBytes(Json.Serialize(data)),null,DataProtectionScope.CurrentUser);
        var tmp=SessionFile+"."+Guid.NewGuid().ToString("N");File.WriteAllBytes(tmp,bytes);
        if(File.Exists(SessionFile))File.Replace(tmp,SessionFile,null);else File.Move(tmp,SessionFile);
    }
    static bool UsesPrism(){Guid id;return !Guid.TryParse(ClientId(),out id);}
    void ShowAccount(){
        if(UsesPrism()){
            signIn.Text="Sign in with Prism";
            account.Text="Account managed in Prism";
            return;
        }
        var s=ReadSession();signIn.Text=s==null?"Sign in with Microsoft":"Sign out";account.Text=s==null?"Signed out":s["name"].ToString();
    }
    void AccountClick(){
        if(UsesPrism()){
            if(!LocatePrism())return;
            Process.Start(new ProcessStartInfo(prefs.PrismExe,"--dir \""+prefs.PrismRoot+"\" --show-window"){UseShellExecute=false});
            status.Text="In Prism, open Settings > Accounts > Add Microsoft. Return here and choose Play instance.";
            return;
        }
        if(File.Exists(SessionFile)){if(MessageBox.Show("Sign out of this launcher?","Microsoft account",MessageBoxButtons.YesNo)==DialogResult.Yes){File.Delete(SessionFile);ShowAccount();}return;}
        RunWorker("signin");
    }
    bool LocatePrism(){
        var exe=new[]{prefs.PrismExe,
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Programs","PrismLauncher","prismlauncher.exe"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),"PrismLauncher","prismlauncher.exe")}.FirstOrDefault(File.Exists);
        if(exe==null){
            using(var picker=new OpenFileDialog{Title="Select Prism Launcher (install PrismLauncher.PrismLauncher with winget if needed)",Filter="Prism Launcher|prismlauncher.exe"}){
                if(picker.ShowDialog(this)!=DialogResult.OK)return false;
                exe=picker.FileName;
            }
        }
        var root=new[]{prefs.PrismRoot,Path.GetDirectoryName(exe),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),"PrismLauncher")}
            .FirstOrDefault(p=>!String.IsNullOrEmpty(p)&&File.Exists(Path.Combine(p,"prismlauncher.cfg")));
        if(root==null){
            using(var picker=new OpenFileDialog{Title="Choose your Prism data folder's configuration file",Filter="Prism settings|prismlauncher.cfg"}){
                if(picker.ShowDialog(this)!=DialogResult.OK)return false;
                root=Path.GetDirectoryName(picker.FileName);
            }
        }
        prefs.PrismExe=exe;prefs.PrismRoot=root;SavePrefs();
        return true;
    }
    Dictionary<string,object> PrismRequest(){
        if(Active==null){status.Text="Select an instance to play.";return null;}
        if(!LocatePrism())return null;
        return new Dictionary<string,object>{{"mode","prism"},{"prismExe",prefs.PrismExe},{"prismRoot",prefs.PrismRoot},{"instance",Active},{"name",instances.SelectedItem.ToString()}};
    }
    async void RunWorker(string mode){
        if(working)return;
        if(mode=="play" && UsesPrism())mode="prism";
        try{
            Guid id=Guid.Empty;
            Dictionary<string,object> request;
            if(mode=="prism"){
                request=PrismRequest();
                if(request==null)return;
            }else{
            if(!Guid.TryParse(ClientId(),out id)){status.Text="Select Launch with Prism to use your Prism account, or configure Void's application ID in Settings.";return;}
            if(mode=="play" && Active==null){status.Text="Select an instance to play.";return;}var saved=ReadSession();
            if(mode=="play"&&(saved==null||!Equals(saved["client"],id.ToString()))){mode="signin-play";}
            request=new Dictionary<string,object>{{"client",id.ToString()},{"mode",mode},{"instance",Active},{"memoryMiB",prefs.MemoryMiB},{"manifest",Path.Combine(Source,"payload","fabric-profile.json")},{"cache",Path.Combine(HomeDir,"game-files")}};
            if(mode=="play" && saved!=null)request["refresh"]=saved["refresh"];
            }
            working=true;status.Text=mode=="prism"?"Opening instance in Prism…":mode=="play"?"Checking your Minecraft account…":"Complete sign-in in your browser.";
            var info=new ProcessStartInfo(Path.Combine(Source,"engine","python.exe"),mode=="prism"?"-B -u launch.py prism":"-B -u launch.py"){WorkingDirectory=Path.Combine(Source,"engine"),UseShellExecute=false,CreateNoWindow=true,RedirectStandardInput=true,RedirectStandardOutput=true};
            info.EnvironmentVariables["PYTHONIOENCODING"]="utf-8";
            using(var worker=Process.Start(info)){
                worker.StandardInput.Write(Json.Serialize(request));worker.StandardInput.Close();string line;
                while((line=await worker.StandardOutput.ReadLineAsync())!=null){
                    var msg=Json.Deserialize<Dictionary<string,object>>(line);
                    if(Equals(msg["kind"],"account")){var result=(Dictionary<string,object>)msg["value"];result["client"]=id.ToString();SaveSession(result);ShowAccount();}
                    else status.Text=msg["value"].ToString();
                }
            }
        }catch(Exception e){status.Text=e.Message;}finally{working=false;}
    }
    [STAThread] static void Main(){
        Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);
        bool setup=Path.GetFileNameWithoutExtension(Application.ExecutablePath).Contains("Setup");
        bool created;
        using(var mutex=new System.Threading.Mutex(true,"Local\\VoidClientDesktop"+(setup?"Setup":"Launcher"),out created)){
            if(!created){MessageBox.Show("Void Client is already open.");return;}Application.Run(new Desktop(setup));
        }
    }

}



