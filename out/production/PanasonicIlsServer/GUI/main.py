# encoding: utf-8

import sys, math, csv, copy
import pygame as pg
from pygame.locals import *
from time import sleep

#######################################################################
# define class
#######################################################################

class Font():
    """
    フォントクラス
    コンストラクタで表示するsurfaceと文字サイズ，色を指定
    usage:
        show("hoge", (10,30))
    """
    def __init__(self, surface, size, rgb):
        self.surface = surface
        self.rgb = rgb
        self.fontType = pg.font.Font('C:/Users/ProjectB-1/Desktop/PanasonicIlsServer/src/GUI/font/URW-Maru-Gothic-5.ttf', size)
        #self.fontType = pg.font.Font('./font/JKG-M_3.ttf', size)
    def show(self, text, pos):
        self.sur = self.fontType.render(text, True, self.rgb)
        self.surface.blit(self.sur, pos)

class SeatStatus:
    """
    画面右の個別の席情報が表示される部分の１つ
    """
    SCALE = 0.79
    FONT_SIZE = 31
    TEXT_LX = ["暗い", "普通", "明るい"]
    TEXT_K = ["寒色", "普通", "暖色"]
    def __init__(self, id: int):
        self.id = id
        self.base1 = pg.image.load(imageDir + 'state_base_true.png').convert_alpha()
        self.base1 = pg.transform.rotozoom(self.base1, 0, self.SCALE)
        self.base2 = pg.image.load(imageDir + 'state_base_false.png').convert_alpha()
        self.base2 = pg.transform.rotozoom(self.base2, 0, self.SCALE)
    def getSurface(self, seating: int, lx: int, k: int):
        seating = int(seating)
        lx = int(lx)
        k = int(k)
        self.idPos = (self.base1.get_width()*0.48, 15)
        self.sPos = (151, 72)
        self.lxPos = (151, 120)
        self.kPos = (151, 168)
        if seating == 1:
            self.tmpSur = copy.copy(self.base1)
            self.font = Font(self.tmpSur, self.FONT_SIZE, (0,0,0))
            self.font.show(str(self.id), self.idPos)
            self.font.show("在席", self.sPos)
            self.font.show(self.TEXT_LX[lx-1], self.lxPos)
            self.font.show(self.TEXT_K[k-1], self.kPos)
        else:
            self.tmpSur = copy.copy(self.base2)
            self.font = Font(self.tmpSur, self.FONT_SIZE, (0,0,0))
            self.font.show(str(self.id), self.idPos)
            self.font.show("離席", self.sPos)
            self.font.show("-", self.lxPos)
            self.font.show("-", self.kPos)
        return self.tmpSur
    def setPos(self, pos):
        self.pos = pos
    def getPos(self):
        return self.pos

class LightStatus:
    """
    照明の個別の明るさ・色温度が表示される部分の１つ
    """
    MAX_RADIUS = (270, 270) # 両方同じ数字で
    WIDTH = 0 # 0で塗りつぶし
    COLORS = [(255,125,255, 150), (0,0,0), (0,0,0)]
    def __init__(self, id: int, surface):
        self.pos = (0,0)
        self.id = id
        self.base = surface
        self.cold1 = pg.image.load(imageDir + 'cold1.png').convert_alpha()
        self.cold2 = pg.image.load(imageDir + 'cold2.png').convert_alpha()
        self.normal = pg.image.load(imageDir + 'normal.png').convert_alpha()
        self.warm1 = pg.image.load(imageDir + 'warm1.png').convert_alpha()
        self.warm2 = pg.image.load(imageDir + 'warm2.png').convert_alpha()
    def show(self, lxPct: float, k: double):
        if double(k) < 3600:
            self.circle = self.cold2
        elif double(k) < 4200:
            self.circle = self.cold1
        elif double(k) < 4800:
            self.circle = self.normal
        elif double(k) < 5400:
            self.circle = self.warm2
        elif 5400 <= double(k):
            self.circle = self.warm1
        self.circle = pg.transform.smoothscale(self.circle, self.MAX_RADIUS)
        self.circle = pg.transform.rotozoom(self.circle, 0, float(lxPct)/100)

        # 描画位置の計算(中心座標から左上を出す)
        self.pos = (self.centerPos[0] - self.circle.get_width()/2,
                    self.centerPos[1] - self.circle.get_height()/2)

        self.base.blit(self.circle, self.pos)
    # def show(self, lxPct: float, k: int):
    #     if int(k) < 3600:
    #         self.circle = self.cold2
    #     elif int(k) < 4200:
    #         self.circle = self.cold1
    #     elif int(k) < 4800:
    #         self.circle = self.normal
    #     elif int(k) < 5400:
    #         self.circle = self.warm2
    #     elif 5400 <= int(k):
    #         self.circle = self.warm1
    #     self.circle = pg.transform.smoothscale(self.circle, self.MAX_RADIUS)
    #     self.circle = pg.transform.rotozoom(self.circle, 0, float(lxPct)/100)
    #
    #     # 描画位置の計算(中心座標から左上を出す)
    #     self.pos = (self.centerPos[0] - self.circle.get_width()/2,
    #                 self.centerPos[1] - self.circle.get_height()/2)
    #
    #     self.base.blit(self.circle, self.pos)
    def setPos(self, centerPos):
        self.centerPos = centerPos
    def getpos(self):
        return self.pos

#######################################################################
# begin
#######################################################################

imageDir = 'C:/Users/ProjectB-1/Desktop/PanasonicIlsServer/src/GUI/image/'
FPS = 5
#SCREEN_RECT = [1600, 900]
SCREEN_RECT = [160*10, 90*10]

pg.init()

def main():
    """ main loop """
    # init
    pg.display.set_caption('知的照明システム稼働状況')
    FPSCLOCK = pg.time.Clock()
    surface = pg.display.set_mode(SCREEN_RECT, DOUBLEBUF) # width, height


    # パーツ作成
    background = pg.image.load(imageDir + 'base.png').convert()
    background = pg.transform.smoothscale(background, SCREEN_RECT)

    seats = []
    for i in range(6):
        seats.append(SeatStatus(i+1))
    seats[0].setPos((1075, 65))
    seats[1].setPos((1335, 65))
    seats[2].setPos((1075, 310))
    seats[3].setPos((1335, 310))
    seats[4].setPos((1075, 555))
    seats[5].setPos((1335, 555))

    lights = []
    for i in range(8):
        lights.append(LightStatus(i+1, surface))
    lights[0].setPos((908,344))
    lights[1].setPos((684,344))
    lights[2].setPos((455,344))
    lights[3].setPos((221,344))
    lights[4].setPos((221,593))
    lights[5].setPos((455,593))
    lights[6].setPos((684,593))
    lights[7].setPos((908,593))


    # テキストラベルの用意
    font = Font(surface, 40, (0,0,0))

    isRunning = True
    isFullscreen = False


    while isRunning:
        f_light = []
        f_seat = []
        # ファイル読み込み
        # with open('./data.csv', 'r') as f:
        with open('C:/Users/ProjectB-1/Desktop/PanasonicIlsServer/src/ilsout.csv', 'r') as f:
            reader = csv.reader(f)
            for i, l in enumerate(reader):
                if i < 8:
                    f_light.append(l)
                else:
                    f_seat.append(l)

        # 背景を描画する
        surface.blit(background, (0, 0))

        # しょうめいの光ぐあいの円をかきかき
        for i, l in enumerate(f_light):
            lights[i].show(l[1], l[2])

        # 画面左の各席の情報を描画
        for i, r in enumerate(f_seat):
            surface.blit(seats[i].getSurface(r[1], r[2], r[3]), seats[i].getPos())

        # surface.blit(seats[0].getSurface(r[1][0], r[1], 0), (1075, 65))
        # surface.blit(seats[1].getSurface(False, 0, 0), (1335, 65))
        # surface.blit(seats[2].getSurface(True, 1, 2), (1075, 310))
        # surface.blit(seats[3].getSurface(False, 0, 0), (1335, 310))
        # surface.blit(seats[4].getSurface(True, 3, 1), (1075, 555))
        # surface.blit(seats[5].getSurface(False, 0, 0), (1335, 555))

        # イベント処理
        for event in pg.event.get():
            if event.type == QUIT or (event.type==KEYUP and event.key==K_q):
                isRunning = False
            elif event.type == KEYUP and event.key == K_f:
                isFullscreen = not isFullscreen
                if isFullscreen:
                    surface = pg.display.set_mode(SCREEN_RECT, FULLSCREEN)
                else:
                    surface = pg.display.set_mode(SCREEN_RECT, 0)

        pg.display.update()
        FPSCLOCK.tick(FPS)
    # 終了処理
    pg.quit()
    sys.exit()

if __name__ == '__main__':
    print("\n------------- はい始め -------------")
    #test()
    main()
