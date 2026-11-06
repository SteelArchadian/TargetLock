# Generates the built-in reticle textures (white + dark outline, tinted in game). Run: python tools/gen_textures.py
import math, os
from PIL import Image, ImageDraw, ImageFilter
S=8; N=64; B=N*S
OUT=os.path.join(os.path.dirname(__file__),'..','src','main','resources','assets','targetlock','textures','reticle')
os.makedirs(OUT,exist_ok=True)
def canvas(): return Image.new('RGBA',(B,B),(0,0,0,0))
def save(im,name): im.resize((N,N),Image.BOX).save(os.path.join(OUT,name))
def poly(pts,fill=(255,255,255,255),edge=(40,25,0,255),w=2.2):
    im=canvas(); d=ImageDraw.Draw(im); p=[(x*S,y*S) for x,y in pts]
    d.polygon(p,fill=edge)
    # inner fill: shrink toward centroid
    cx=sum(x for x,_ in p)/len(p); cy=sum(y for _,y in p)/len(p)
    k=lambda x,y,s:(cx+(x-cx)*s,cy+(y-cy)*s)
    return im,d,p,(cx,cy)
# triangle pointing down (apex at bottom)
im=canvas(); d=ImageDraw.Draw(im)
d.polygon([(16*S,8*S),(48*S,8*S),(32*S,58*S)],fill=(60,40,0,255))
d.polygon([(19.5*S,11*S),(44.5*S,11*S),(32*S,50*S)],fill=(255,255,255,255)); save(im,'triangle.png')
# down arrow with segmented stem
im=canvas(); d=ImageDraw.Draw(im)
def arrow(col,off=0):
    d.polygon([(32*S,60*S-off),(12*S,34*S),(24*S,34*S),(24*S,24*S),(40*S,24*S),(40*S,34*S),(52*S,34*S)],fill=col)
arrow((40,25,0,255)); 
d.polygon([(32*S,54*S),(18*S,36.5*S),(26.5*S,36.5*S),(26.5*S,26.5*S),(37.5*S,26.5*S),(37.5*S,36.5*S),(46*S,36.5*S)],fill=(255,255,255,255))
d.rectangle([26.5*S,16*S,37.5*S,20*S],fill=(40,25,0,255)); d.rectangle([28*S,17*S,36*S,19*S],fill=(255,255,255,255))
d.rectangle([28.5*S,8*S,35.5*S,12*S],fill=(40,25,0,255)); d.rectangle([30*S,9*S,34*S,11*S],fill=(255,255,255,255))
save(im,'arrow.png')
