package dev.voidclient;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

final class HudPosition extends Screen {
    private final Screen parent;
    private HudLayout.Frame[] frames;
    private HudLayout.Frame start;
    private HudLayout.Panel selected=HudLayout.Panel.READOUTS;
    private double startX,startY;
    private boolean resize;

    HudPosition(Screen parent){
        super(Text.literal("Void HUD layout"));
        this.parent=parent;
    }

    @Override public void render(DrawContext g,int mx,int my,float dt){
        frames=HudLayout.draw(g,true);
        for(var panel:HudLayout.Panel.values()){
            var f=frames[panel.ordinal()];
            int x=f.x(),y=f.y(),r=x+f.width(),b=y+f.height();
            int color=panel==selected?VoidClient.accent():0xFF8994A4;
            g.fill(x,y,r,y+1,color);g.fill(x,b-1,r,b,color);
            g.fill(x,y,x+1,b,color);g.fill(r-1,y,r,b,color);
            g.fill(r-5,b-5,r,b,color);
        }
        String label=switch(selected){case READOUTS -> "Readouts"; case ICONS -> "Utility icons"; case EFFECTS -> "Status effects";};
        g.drawCenteredTextWithShadow(textRenderer,label+"  "+HudLayout.value(selected.key+".scale",80,45,240)+"%",width/2,height-24,0xFFFFFFFF);
        g.drawCenteredTextWithShadow(textRenderer,"Drag to move • Drag corner to scale • Tab to select • Esc to finish",width/2,height-12,0xFFD3D6DB);
    }

    @Override public boolean mouseClicked(Click click,boolean twice){
        if(click.button()!=0 || frames==null)return false;
        if(!frames[selected.ordinal()].contains(click.x(),click.y())){
            var hit=java.util.Arrays.stream(HudLayout.Panel.values())
                .filter(p->frames[p.ordinal()].contains(click.x(),click.y())).findFirst();
            if(hit.isEmpty())return false;
            selected=hit.get();
        }
        start=frames[selected.ordinal()];startX=click.x();startY=click.y();
        resize=startX>=start.x()+start.width()-6 && startY>=start.y()+start.height()-6;
        // Once editing either panel, keep the other one stationary.
        for(var panel:HudLayout.Panel.values())if(panel!=selected){
            var sibling=frames[panel.ordinal()];
            HudLayout.move(panel,sibling.x(),sibling.y(),width-sibling.width(),height-sibling.height());
        }
        return true;
    }

    @Override public boolean mouseDragged(Click click,double dx,double dy){
        if(start==null || click.button()!=0)return false;
        double changeX=click.x()-startX,changeY=click.y()-startY;
        int x=start.x(),y=start.y(),w=start.width(),h=start.height();
        if(resize){
            double nw=start.naturalWidth(),nh=start.naturalHeight();
            double delta=(changeX*nw+changeY*nh)/(nw*nw+nh*nh);
            int percent=Math.clamp((int)Math.round((start.scale()+delta)*100),45,240);
            VoidClient.cfg.setProperty(selected.key+".scale",Integer.toString(percent));
            float actualScale=Math.min(percent/100f,Math.min(width/(float)nw,height/(float)nh));
            w=(int)Math.ceil(nw*actualScale);h=(int)Math.ceil(nh*actualScale);
        }else{
            x+=(int)Math.round(changeX);y+=(int)Math.round(changeY);
        }
        HudLayout.move(selected,x,y,width-w,height-h);
        return true;
    }

    @Override public boolean mouseReleased(Click click){
        if(start==null || click.button()!=0)return false;
        start=null;VoidClient.save();return true;
    }

    @Override public boolean keyPressed(net.minecraft.client.input.KeyInput key){
        if(key.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_TAB && start==null){
            var panels=HudLayout.Panel.values();
            selected=panels[(selected.ordinal()+1)%panels.length];
            return true;
        }
        return super.keyPressed(key);
    }

    @Override public void removed(){VoidClient.save();}
    @Override public void close(){client.setScreen(parent);}
}
