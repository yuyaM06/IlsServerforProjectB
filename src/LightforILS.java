import LightingSystem.Light;

import java.util.Random;

public class LightforILS extends Light {
    int id, lumPct, lightTemp;

    LightforILS(int id, int lumPct){
        this.id = id;
        this.lumPct = lumPct;
    }

    public void setLumPctBefore(int lumPct){
        this.lumPct = lumPct;
    }

    public void setLumPctAfter(int lumPct){
        this.lumPct = lumPct;
    }

    public int nextlumPct(){
        Random rand = new Random();
        while(true){
             lumPct = (int)(this.lumPct * (0.92 + Math.random()*0.14));
            if (lumPct >= 0 && lumPct <= 100) break;
        }
        return lumPct;
    }


}
