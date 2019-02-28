import LightingSystem.Light;
import LightingSystem.SocketClient;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/*
 * Ilsserverを確保した時点で
 * 1. IlsHttpServerを立てる(192.168.1.30:8765)（HTTP通信）
 * 2. 調光サーバのEndPointを設定(192.168.1.30:44344)（ソケット通信）
 * 3. lightのインスタンスの作成
 * 4. 最小点灯で準備
 *
 * iPhoneからtokenを受け取り次第，調光
 * */



public class IlsServer {
    static ArrayList<Light> lights = new ArrayList<>();
    final static int LIGHT_NUM = 8;
    final static int LIGHT_DATA_NUM = 3;
    final static int SEAT_NUM = 6;
    final static int SEAT_DATA_NUM = 4;
    static ArrayList<Integer> seats = new ArrayList<>();    //座っている席のidList

    IlsHttpServer srv = null;

    IlsServer(String host, Integer port){
        srv = new IlsHttpServer(8765);
        srv.start();

        /* 調光サーバへのアクセス準備 */
        SocketClient.setEndpoint(new InetSocketAddress(host, 44344)); //port:44344はSimpleLightingControlServerのSocketServerのPORTと合わせる

        /* lightのインスタンス準備 */
        for(int i=0; i<LIGHT_NUM; i++){
            Light l = new Light();
            l.setLumPct(10);
            lights.add(l);
        }
        minDimming(lights);
    }

    public void run(){
        System.out.println("running...");
        while(true){
            HashMap req = srv.getSingleRequest();
            if(req == null){
//                System.out.println("...No request...");
                try {
                    Thread.sleep(1500);
                }catch(Exception e){
                    e.printStackTrace();
                }
            }else{
                System.out.println(req);
                ils(req);
            }
        }
    }

    void ils(HashMap req){
        int targetSeatId = Integer.parseInt(req.get("id").toString());
        int targetLx = Integer.parseInt(req.get("lx").toString());
        int targetK = Integer.parseInt(req.get("k").toString());

        if(!seats.contains(targetSeatId)) seats.add(targetSeatId);
        if(targetLx == 0) seats.remove(seats.indexOf(targetSeatId));

        /* 在席中の席リスト表示 */
        System.out.println("seatsSize:" + seats.size());
        System.out.println(seats);

        /*
        targetLx
            1:300lx, 2:500lx　3:700lx
        targetK
            1:3000K, 2:4500K, 3:6000K
        */
        System.out.println("targetSeatId: " + targetSeatId + ", targetLx: " + targetLx + ", targetK: " + targetK);

        //Variable for GUI
        double[][] lightInfo = new double[LIGHT_NUM][LIGHT_DATA_NUM];
        int[][] seatInfo = new int[SEAT_NUM][SEAT_DATA_NUM];

        /* Initialize */
        for (int i = 0; i < LIGHT_NUM; i++) {
            lightInfo[i][0] = i;    //lightId
            lightInfo[i][1] = 0;    //lumPct
            lightInfo[i][2] = 0;    //lightTemp
        }
        for (int i = 0; i < SEAT_NUM; i++) {
            seatInfo[i][0] = i + 1; //seatId
            seatInfo[i][1] = 0;     //seating
            seatInfo[i][2] = 0;     //lumPt
            seatInfo[i][3] = 0;     //lightTempPt
        }

        // GUI用のデータを埋める（for Seats）
        if(seats.contains(targetSeatId) && targetLx != 0 && targetK != 0){
            seatInfo[targetSeatId -1][1] = 1;           //seating
            seatInfo[targetSeatId -1][2] = targetLx;
            seatInfo[targetSeatId -1][3] = targetK;
        }else{
            seatInfo[targetSeatId -1][1] = 0;
            seatInfo[targetSeatId -1][2] = targetLx;
            seatInfo[targetSeatId -1][3] = targetK;
        }

        /* paramsの各パターンを変換 */
        switch(targetLx){
            case 0 :
                targetLx = 100;
                break;
            case 1:
                targetLx = 300;
                break;
            case 2:
                targetLx = 500;
                break;
            case 3:
                targetLx = 700;
                break;
        }
        switch(targetK){
            case 0:
                targetK = 4500;
                break;
            case 1:
                targetK = 3000;
                break;
            case 2:
                targetK = 4500;
                break;
            case 3:
                targetK = 6000;
                break;
        }

        /* 影響度係数の読み込み */
        File file = new File("src/influenceFutureOffice.csv");
        double[][] infArray = readFile(file);
        //for debug
        for(int i=0; i<SEAT_NUM; i++){
            for(int j=0; j<LIGHT_NUM; j++){
                System.out.print(infArray[i][j] + ",");
            }
            System.out.println();
        }

        /* 対象となる席に対する影響度だけ抽出 */
        double[] targetSeatInf = infArray[targetSeatId - 1]; //SeatId : 1 ~ 6のため，-1
        int[] infRank = getRankofInf(targetSeatInf);
        ArrayList<Integer> infRankList = new ArrayList<>(); //index検索用(∵配列の検索面倒)
        for (int rank : infRank) infRankList.add(rank);     //影響度係数を大きい順にrank付けし，そのindexを検索するためのArrayList
        //for debug
        for (int i=0; i<targetSeatInf.length; i++)
            System.out.print(targetSeatInf[i] + ":" + infRank[i] + ",");


        /* SHCで頑張ります */
        while(true) {
            System.out.println("\ntargetLx:" + targetLx);

            /* 照度計算 */
            double tmp = 0;
            double[] beforelumPct = new double[LIGHT_NUM];
            for (int i = 0; i < LIGHT_NUM; i++) {
                tmp += targetSeatInf[i] * lights.get(i).getLumPct() * 13;   //従来のプログラムでは最大光度=1300 cdで 13cd/1%の計算（ノリ）
                beforelumPct[i] = lights.get(i).getLumPct();                //棄却時に復元できるように確保
            }

            double beforeLx = tmp;
            double beforeGap = (targetLx - beforeLx) * (targetLx - beforeLx);

            System.out.println("\nbeforeLx:" + beforeLx);

            /* 光度設定（lumPct） */
//            for (Light l : lights) setNextlumPct(l);            //0 ~ 7の照明すべてに対して光度設定
//            setNextlumPct(lights.get((int)(Math.random()*8)));  //0 ~ 7の照明のどれかをランダムで光度設定
            final int dimmingRange = 2;  //2:spot感，4:広め
            if(targetLx != 0) setNextlumPct(lights.get(infRankList.indexOf((int)(Math.random()*dimmingRange) +1)));  //影響度Rank 1 ~ searchRange(=2)までの照明のどれかをランダムで光度設定
            else if(targetLx == 0) setNextlumPctLower(lights.get(infRankList.indexOf((int)(Math.random()*dimmingRange) +1)));   //離席に対しては，光度が下がるように

            /* 照度再計算 */
            tmp = 0;
            for (int i = 0; i < LIGHT_NUM; i++)
                tmp += targetSeatInf[i] * lights.get(i).getLumPct() * 13;
            double afterLx = tmp;
            double afterGap = (targetLx - afterLx) * (targetLx - afterLx);

            System.out.println("afterLx:" + afterLx);

            //もし誤差が改善していなければ，元に戻す
            if (beforeGap - afterGap < 0)
                for (int i = 0; i < LIGHT_NUM; i++)lights.get(i).setLumPct(beforelumPct[i]);

            if (Math.abs(targetLx - afterLx) < targetLx * 0.20) break;
        }

        /* 色温度の設定 */    //色温度変更範囲を変更する可能性あり
        lights.get(infRankList.indexOf(1)).setTemperature(targetK);

        // 調光およびGUI用のデータを埋める（for Lights）
        for (Light l : lights){
            lightInfo[l.getId() - 1][1] = l.getLumPct();
            lightInfo[l.getId() - 1][2] = l.getTemperature();
        }

        /* 調光 */
        dimming(lightInfo);

        /* 各照明の情報（知的照明の結果）コンソール上に出力 */
        for(int i=0; i<LIGHT_NUM; i++){
            System.out.print(lightInfo[i][0]);
            System.out.print(",");
            System.out.print(lightInfo[i][1]);
            System.out.print(",");
            System.out.println(lightInfo[i][2]);
        }

        /* 各照明の情報（知的照明の結果）をcsvに出力 */
        outFile(lightInfo, seatInfo);
    }

    /* csvから影響度係数を読み出す */
    public static double[][] readFile(File file){
        double[][] infArray = new double[6][8];

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            br.readLine();

            String line;
            int rowcount = 0;
            int columncount = 0;

            // 1行ずつCSVファイルを読み込む
            while ((line = br.readLine()) != null) {
                String[] data = line.split(",", 0); // 行をカンマ区切りで配列に変換

                for(String elem : data) {
//                    System.out.println(elem);
                    infArray[rowcount][columncount] = Double.parseDouble(elem);
                    columncount ++;
                }
                rowcount ++;
                columncount = 0;
            }
            br.close();
        } catch (IOException e) { System.out.println(e); }

        return infArray;
    }

    /* 各照明の情報（知的照明の結果）をcsvに出力 */
    public static void outFile(double[][] lightInfo, int[][]seatInfo){
        File file = new File("src/ilsout.csv");
        /*
        csvの出力内容
        lightId, lumPct, lightTemp : 8行
        seatId, isSeat, lumPt, lightTempPt : 6行
        */

        try{
            PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8")));

            for(int i=0; i<lightInfo.length; i++){
                pw.write(String.valueOf(lightInfo[i][0]));
                pw.write(",");
                pw.write(String.valueOf(lightInfo[i][1]));
                pw.write(",");
                pw.write(String.valueOf(lightInfo[i][2]));
                pw.write("\n");
            }

            for(int i=0; i<seatInfo.length; i++){
                pw.write(String.valueOf(seatInfo[i][0]));
                pw.write(",");
                pw.write(String.valueOf(seatInfo[i][1]));
                pw.write(",");
                pw.write(String.valueOf(seatInfo[i][2]));
                pw.write(",");
                pw.write(String.valueOf(seatInfo[i][3]));
                pw.write("\n");
            }
            pw.close();

        }catch(IOException ex){ ex.printStackTrace(); }

        System.out.println("Output CSV for GUI is done!!");
    }

    /* 目標の席に対する影響度係数のRankを算出 */
    public static int[] getRankofInf(double[] targetSeatInf){
        int index = 0;
        double max = 0;
        int[] infRank = new int[targetSeatInf.length];

        int i=0;
        while (i < targetSeatInf.length) {
            infRank[i] = 1;
            int j = 0;
            while (j < targetSeatInf.length) {
                if (targetSeatInf[i] < targetSeatInf[j]) {
                    infRank[i] = infRank[i] + 1;
                }
                j++;
            }
            i++;
        }

        return infRank;
    }

    /* 照明の光度(lumPct)をランダムに設定 */
    public static void setNextlumPct(Light l){
        int loopcount = 0;
        Random rand = new Random();
        while(true){
            System.out.println( "dimmerRate" + 0.92 + Math.random()*0.14);
            l.setLumPct(l.getLumPct() * (0.92 + Math.random()*0.14));
            if (l.getLumPct() >= 0 && l.getLumPct() <= 100) break;
            if(l.getLumPct() > 100) l.setLumPct(100);
            if(l.getLumPct() < 1) l.setLumPct(0);
            if (loopcount > 1000) break;
        }
    }

    public static void setNextlumPctUpper(Light l){
        Random rand = new Random();
        while(true){
            System.out.println("dimmerRate" + (1.0 + Math.random()*0.06));
            l.setLumPct(l.getLumPct() * 1.0 + l.getLumPct() * Math.random()*0.06);
            System.out.println(l.getLumPct());
            if (l.getLumPct() >= 0 && l.getLumPct() <= 100) break;
            if(l.getLumPct() > 100) l.setLumPct(100);
        }
    }

    /* 照明の光度(lumPct)を下がるようにランダム設定 */
    public static void setNextlumPctLower(Light l){
        Random rand = new Random();
        while(true){
            System.out.println("dimmerRate" +  (0.92 + Math.random()*0.08));
            l.setLumPct(l.getLumPct() * 0.92 + l.getLumPct() * Math.random()*0.08);
            if (l.getLumPct() >= 0 && l.getLumPct() <= 100) break;
        }
    }

    /* 照明の光度(lumPct)を最小点灯に設定 */
    public void minDimming(ArrayList<Light> lights) {
        //個別制御
        for (int i = 0; i < 8; i++) {
            lights.get(i).setLumPct(5);
            lights.get(i).setTemperature(4500);
        }
        System.out.println(SocketClient.dimByLights(lights));
    }
    /* 調光処理 */
    public void dimming(double[][] lightInfo) {

        //個別制御
        //lightInfoをもとに調光
        for (int i=0; i < 8; i++) {
            lights.get(i).setLumPct(lightInfo[i][1]);
            lights.get(i).setTemperature(lightInfo[i][2]);
        }

        //照明個別調光（白色・電球色交互）
        System.out.println(SocketClient.dimByLights(lights));

    }
}
