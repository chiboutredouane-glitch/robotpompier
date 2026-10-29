#!/usr/bin/env python3
"""Draw the original app icon; no external images or dependencies."""
from pathlib import Path
import json,math,struct,zlib
root=Path(__file__).resolve().parents[1]/'App/Assets.xcassets'
root.mkdir(parents=True,exist_ok=True)
(root/'Contents.json').write_text(json.dumps({'info':{'author':'xcode','version':1}}))
d=root/'AppIcon.appiconset';d.mkdir(exist_ok=True)
w=1024;navy=(16,23,34);yellow=(255,199,32);cyan=(23,217,236)
segments=[((650,220),(650,804)),((650,220),(835,400)),((835,400),(540,650)),((540,650),(835,820)),((835,820),(650,620))]
def line(x,y,a,b,r):
 ax,ay=a;bx,by=b;dx=bx-ax;dy=by-ay;t=max(0,min(1,((x-ax)*dx+(y-ay)*dy)/(dx*dx+dy*dy)))
 return (x-ax-t*dx)**2+(y-ay-t*dy)**2<=r*r
rows=[]
for y in range(w):
 row=bytearray([0])
 for x in range(w):
  color=navy
  if (170<x<280 and 300<y<720) or (65<x<385 and 455<y<565):color=yellow
  if any(line(x,y,a,b,28) for a,b in segments):color=cyan
  row.extend(color)
 rows.append(bytes(row))
def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,w,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows),9))+chunk(b'IEND',b'')
(d/'AppIcon.png').write_bytes(png)
(d/'Contents.json').write_text(json.dumps({'images':[{'filename':'AppIcon.png','idiom':'universal','platform':'ios','size':'1024x1024'}],'info':{'author':'xcode','version':1}},indent=2))
print('Icône originale générée')
