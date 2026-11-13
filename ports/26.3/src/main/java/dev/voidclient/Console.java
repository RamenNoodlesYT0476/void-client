package dev.voidclient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class Console extends Screen {
    int group=0;
    final String[] groups={"Combat","Utility","HUD","Access"};
    int tab=0,page=0,offset=0,serial=0; String query="",status="";
    List<Catalog.Hit> hits=List.of(); Catalog.Hit selected; Catalog.Release file;
    EditBox field; boolean busy;
    final String[] tabs={"Utilities","Settings","Modrinth","CurseForge"};
    int x,y,w,h,body,cols,rows;
    Console(){super(Component.literal("Void Client"));}
    private void openLink(String url){try{java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));}catch(Exception ex){status="Could not open browser";}}
    int right(){return x+w-9;}
    void button(int bx,int by,int bw,String label,Runnable fn){addRenderableWidget(Button.builder(Component.literal(label),b->fn.run()).bounds(bx,by,bw,18).build());}
    record Tile(Button button,VoidClient.Module module){}
    final List<Tile> tiles=new ArrayList<>();
    void refresh(){clearWidgets();init();}
    void switchTab(int i){serial++;tab=i;page=offset=0;selected=null;file=null;busy=false;query=status="";hits=List.of();refresh();if(tab>=2)search();}
    @Override protected void init(){
        tiles.clear();
        w=Math.min(width-10,490);h=Math.min(height-8,308);x=(width-w)/2;y=(height-h)/2;body=y+68;
        rows=Math.max(1,(h-106)/21);cols=w>=355?2:1;
        int tw=(w-18)/4;for(int i=0;i<4;i++){int n=i;button(x+9+i*tw,y+30,tw-2,tabs[i],()->switchTab(n));}
        if(tab==0){
            int gw=(w-18)/groups.length;
            for(int i=0;i<groups.length;i++){int id=i;button(x+9+i*gw,body,gw-2,(group==i?"› ":"")+groups[i],()->{group=id;page=0;refresh();});}
            var entries=Preferences.group(group);
            int across=Math.max(1,(w-18)/28),capacity=across*Math.max(1,(h-127)/25),count=entries.size();
            page=Math.max(0,Math.min(page,(count-1)/capacity));
            for(int i=0;i<capacity && page*capacity+i<count;i++){
                var m=entries.get(page*capacity+i);
                int bx=x+9+i%across*28,by=body+22+i/across*25;
                var tile=Button.builder(Component.literal(m.label()),button->{
                    if(m.hud()!=null)VoidClient.set("module."+m.id(),String.valueOf(!VoidClient.enabled(m)));
                    else if(minecraft.player!=null)m.action().accept(minecraft);
                    refresh();
                }).bounds(bx,by,25,22).build();
                tile.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(m.label())));
                addRenderableWidget(tile);
                tiles.add(new Tile(tile,m));
            }            pager(count,capacity);
        }else if(tab==1){
            String[][] controls={
                {"accent","Menu accent","Neon red","Purple","Blue","Grey"},
                {"anchor","HUD corner","Top left","Top right","Bottom left","Bottom right"},
                {"position","HUD layout","Move / resize"},
                {"hud.hidden","HUD","Visible","Hidden"},
                {"hud.ink","HUD text","Grey","Menu accent"},
                {"hud.tint","Panel tint","Clear","9%","19%"},
                {"hud.spacing","Row spacing","Compact","Normal","Wide"},
                {"combat.range","Attack range","2 blocks","2.5 blocks","3 blocks"},
                {"combat.weapon","Kill Aura weapon","Swords","Any"},
                {"combat.cooldown","Attack cooldown","95%","85%","75%"},
                {"crystal.mode","Crystal Aura","Place + break","Place","Break"},
                {"crystal.buffer","Aura minimum self-distance","2 blocks","2.5 blocks","3 blocks","3.5 blocks","4 blocks"},
                {"crystal.warningRadius","Crystal warning radius","4 blocks","6 blocks","8 blocks","10 blocks"},
                {"survival.totems","Warn at remaining totems","0","1","2","3"},
                {"crystal.delay","Crystal delay","2 ticks","4 ticks","6 ticks","8 ticks"},
                {"alerts.health","Health alert","2 hearts","3 hearts","4 hearts","5 hearts"},
                {"alerts.sound","Alert sound","On","Off"}
            };
            int capacity=cols*rows;
            for(int i=0;i<capacity && page*capacity+i<controls.length;i++){
                String[] setting=controls[page*capacity+i];int count=setting.length-2;
                int bw=(w-18)/cols;
                button(x+9+(i%cols)*bw,body+(i/cols)*21,bw-3,
                    setting[1]+" / "+setting[2+VoidClient.number(setting[0],count)],
                    ()->{if(setting[0].equals("position")){minecraft.gui.setScreen(new HudPosition(this));return;}
                        if(setting[0].equals("anchor"))HudLayout.resetPositions();
                        Preferences.cycle(setting[0],count);refresh();});
            }
            pager(controls.length,capacity);        }else if(selected!=null){
            button(x+9,body+20,(w-22)/2,"Project & dependencies ↗",()->openLink(selected.page()));
            button(x+11+(w-22)/2,body+20,(w-22)/2,"Back to results",()->{serial++;busy=false;selected=null;file=null;status="";refresh();});
            if(file!=null&&!busy)button(x+9,body+43,w-18,"Install verified JAR on restart",()->{var chosen=file;file=null;job(()->{Catalog.install(chosen);return "Installed. Restart Minecraft to load.";},s->status=s);});
        }else{
            field=new EditBox(font,x+9,body,w-148,18,Component.literal("Search mods"));field.setMaxLength(120);field.setValue(query);field.setResponder(s->query=s);addRenderableWidget(field);
            button(right()-125,body,60,"Search",()->{offset=page=0;search();});button(right()-62,body,62,"Website ↗",()->openLink(tab==3?"https://www.curseforge.com/minecraft/search?class=mc-mods&search="+Catalog.enc(query):"https://modrinth.com/mods?q="+Catalog.enc(query)));
            int cap=Math.max(1,rows-1);for(int i=0;i<cap&&page*cap+i<hits.size();i++){var hit=hits.get(page*cap+i);button(x+9,body+22+i*21,w-18,hit.title(),()->{selected=hit;file=null;boolean cf=tab==3;job(()->Catalog.resolve(cf,hit),v->{file=v;status=v.dependencies()+" required dependencies • review project before installing";});});}
            pager(hits.size(),cap);
        }
    }
    void cycle(String k){VoidClient.set(k,""+((VoidClient.number(k,4)+1)%4));refresh();}
    void pager(int count,int capacity){
        int by=y+h-23;if(page>0||offset>0)button(x+9,by,48,"‹ Prev",()->{if(page>0){page--;refresh();}else{offset-=12;page=0;search();}});
        if((page+1)*capacity<count||(tab>=2&&count==12))button(right()-48,by,48,"Next ›",()->{if((page+1)*capacity<count){page++;refresh();}else{page=0;offset+=12;search();}});
    }
    void search(){var q=query;var cf=tab==3;var start=offset;hits=List.of();job(()->Catalog.search(cf,q,start),v->{hits=v;status=v.isEmpty()?"No matching projects":(start+1)+"–"+(start+v.size())+" projects";});}
    <T> void job(Callable<T> fn,Consumer<T> done){
        int token=++serial;busy=true;status="Working…";refresh();var mc=minecraft;
        CompletableFuture.runAsync(()->{try{T result=fn.call();mc.execute(()->{if(token==serial&&mc.gui.screen()==this){busy=false;done.accept(result);refresh();}});}
            catch(Exception e){mc.execute(()->{if(token==serial&&mc.gui.screen()==this){busy=false;status=Objects.toString(e.getMessage(),"Request failed");refresh();}});}});
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent key){if(tab>=2&&selected==null&&key.key()==257){page=offset=0;search();return true;}return super.keyPressed(key);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        int accent=VoidClient.accent();g.fill(x,y,x+w,y+h,0xFB101017);g.fill(x,y,x+w,y+2,accent);
        g.fill(x,y+25,x+w,y+27,0xFF382743);g.fill(x+9,y+50+0,x+9+(w-18)/4*4,y+51,0xFF393541);
        int tw=(w-18)/4;g.fill(x+9+tab*tw,y+49,x+7+(tab+1)*tw,y+51,accent);
        g.text(font,"V O I D   /   CLIENT",x+9,y+10,accent,false);
        g.text(font,"FABRIC · 26.3",right()-font.width("FABRIC · 26.3"),y+10,0xFF94A4BD,false);
        String label=tab==0?"MODULES":tab==1?"02   /   INTERFACE":tab==2?"03   /   MODRINTH":"04   /   CURSEFORGE";
        g.text(font,label,x+9,y+56,0xFFC1B3CE,false);
        if(selected!=null)g.text(font,font.plainSubstrByWidth(file==null?selected.title():file.name(),w-18),x+9,body+4,0xFFEAE4F2,false);
        String note=status.isBlank()?(tab==0?"MouseButtonEvent to toggle or run":tab==1?"Preferences save automatically":"Fabric 26.3 • stable releases"):status;
        var lines=font.split(Component.literal(note),Math.max(80,w-124));int top=y+h-17-(Math.min(2,lines.size())-1)*9;
        for(int i=0;i<Math.min(2,lines.size());i++)g.text(font,lines.get(i),x+61,top+i*9,0xFFABB8CD,false);
        super.extractRenderState(g,mx,my,dt);
        for(var tile:tiles){
            var btn=tile.button();var m=tile.module();int bx=btn.getX(),by=btn.getY();
            boolean active=m.hud()!=null?VoidClient.enabled(m):m.id().startsWith("switch.")&&VoidClient.switchOn(m.id().substring(7));
            g.fill(bx+1,by+1,bx+24,by+21,btn.isHovered()||btn.isFocused()?0xFF45414B:0xFF232228);
            g.item(ModuleIcons.item(m.id()).getDefaultInstance(),bx+4,by+2);
            if(active)g.fill(bx+3,by+18,bx+22,by+20,accent);
        }
    }
}







