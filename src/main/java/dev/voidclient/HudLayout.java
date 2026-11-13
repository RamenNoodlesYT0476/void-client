package dev.voidclient;

import java.util.ArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

final class HudLayout {
    enum Panel {
        READOUTS("hud"), ICONS("icons"), EFFECTS("effects");
        final String key;
        Panel(String key){this.key=key;}
    }

    record Frame(int x,int y,int width,int height,int naturalWidth,int naturalHeight,float scale) {
        boolean contains(double mx,double my){
            return mx>=x && my>=y && mx<x+width && my<y+height;
        }
    }

    static void resetPositions(){
        for(var panel:Panel.values())VoidClient.cfg.remove(panel.key+".custom");
        VoidClient.save();
    }

    static int value(String key,int fallback,int low,int high){
        try{return Math.clamp(Integer.parseInt(VoidClient.cfg.getProperty(key,Integer.toString(fallback))),low,high);}
        catch(NumberFormatException ignored){return fallback;}
    }

    static void move(Panel panel,int x,int y,int availableX,int availableY){
        VoidClient.cfg.setProperty(panel.key+".custom","true");
        VoidClient.cfg.setProperty(panel.key+".x",Integer.toString(normalized(x,availableX)));
        VoidClient.cfg.setProperty(panel.key+".y",Integer.toString(normalized(y,availableY)));
    }

    static int normalized(int pixel,int available){
        return available<=0?0:Math.clamp((int)Math.round(1000.0*pixel/available),0,1000);
    }

    static Frame frame(Panel panel,int screenW,int screenH,int naturalW,int naturalH,Frame parent){
        float scale=value(panel.key+".scale",80,45,240)/100f;
        scale=Math.min(scale,Math.min(screenW/(float)naturalW,screenH/(float)naturalH));
        int w=(int)Math.ceil(naturalW*scale),h=(int)Math.ceil(naturalH*scale);
        int ax=Math.max(0,screenW-w),ay=Math.max(0,screenH-h);
        int anchor=VoidClient.number("anchor",4);
        int x=anchor%2==1?ax-4:4,y=anchor>=2?ay-22:4;
        if(panel==Panel.EFFECTS){x=4;y=ay-4;}
        if(parent!=null){
            x=parent.x();
            y=parent.y()+parent.height()+2;
            if(y>ay)y=parent.y()-h-2;
        }
        if(Boolean.parseBoolean(VoidClient.cfg.getProperty(panel.key+".custom","false"))){
            x=Math.round(ax*value(panel.key+".x",0,0,1000)/1000f);
            y=Math.round(ay*value(panel.key+".y",0,0,1000)/1000f);
        }
        return new Frame(Math.clamp(x,0,ax),Math.clamp(y,0,ay),w,h,naturalW,naturalH,scale);
    }

    static Frame[] draw(DrawContext g,boolean editing){
        var client=MinecraftClient.getInstance();
        var lines=new ArrayList<String>();
        var icons=new ArrayList<VoidClient.Module>();
        for(var module:VoidClient.modules){
            if(module.hud()!=null && VoidClient.enabled(module)){
                String line=module.hud().apply(client);
                if(line!=null && !line.isBlank())lines.add(line);
            }
            if(module.id().startsWith("switch.") && VoidClient.switchOn(module.id().substring(7)))icons.add(module);
        }
        if(editing && lines.isEmpty())lines.add("HUD readouts");
        if(editing && icons.isEmpty())VoidClient.modules.stream().filter(m->m.id().startsWith("switch.")).limit(3).forEach(icons::add);
        int sw=g.getScaledWindowWidth(),sh=g.getScaledWindowHeight();
        int spacing=9+VoidClient.number("hud.spacing",3);
        int ink=VoidClient.number("hud.ink",2)==0?0xFFE1E3EF:VoidClient.accent();
        int textWidth=Math.min(sw,4+lines.stream().mapToInt(client.textRenderer::getWidth).max().orElse(0));
        Frame text=frame(Panel.READOUTS,sw,sh,Math.max(4,textWidth),Math.max(2,2+lines.size()*spacing),null);
        Frame strip=frame(Panel.ICONS,sw,sh,Math.max(16,icons.size()*16),16,lines.isEmpty()?null:text);
        if(!lines.isEmpty()){
            begin(g,text);
            for(int i=0;i<lines.size();i++)g.drawTextWithShadow(client.textRenderer,client.textRenderer.trimToWidth(lines.get(i),textWidth-4),2,1+i*spacing,ink);
            g.getMatrices().popMatrix();
        }
        if(!icons.isEmpty()){
            begin(g,strip);
            for(int i=0;i<icons.size();i++)g.drawItem(ModuleIcons.item(icons.get(i).id()).getDefaultStack(),i*16,0);
            g.getMatrices().popMatrix();
        }
        var sprites=new ArrayList<net.minecraft.util.Identifier>();
        if(Boolean.parseBoolean(VoidClient.cfg.getProperty("module.statusEffects","true"))){
            for(var effect:client.player.getStatusEffects())if(effect.shouldShowIcon())
                sprites.add(net.minecraft.client.gui.hud.InGameHud.getEffectTexture(effect.getEffectType()));
            sprites.sort(java.util.Comparator.comparing(Object::toString));
        }
        if(editing && sprites.isEmpty())
            sprites.add(net.minecraft.client.gui.hud.InGameHud.getEffectTexture(net.minecraft.entity.effect.StatusEffects.SPEED));
        int columns=Math.max(1,Math.min(8,sw/16));
        Frame effects=frame(Panel.EFFECTS,sw,sh,Math.max(1,Math.min(columns,sprites.size()))*16,
            Math.max(1,(sprites.size()+columns-1)/columns)*16,null);
        if(!sprites.isEmpty()){
            begin(g,effects);
            for(int i=0;i<sprites.size();i++)
                g.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,sprites.get(i),i%columns*16,i/columns*16,15,15);
            g.getMatrices().popMatrix();
        }
        return new Frame[]{text,strip,effects};
    }

    private static void begin(DrawContext g,Frame frame){
        var matrices=g.getMatrices();
        matrices.pushMatrix();matrices.translate((float)frame.x(),(float)frame.y());matrices.scale(frame.scale(),frame.scale());
        int alpha=VoidClient.number("hud.tint",3)*24;
        if(alpha>0)g.fill(0,0,frame.naturalWidth(),frame.naturalHeight(),alpha<<24);
    }
}
