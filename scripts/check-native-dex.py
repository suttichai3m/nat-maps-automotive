#!/usr/bin/env python3
"""Offline anchor verification for the original Maps 26.10.120003.E APK (no dependencies)."""
import zipfile,struct,sys
from array import array
U=lambda b,o:struct.unpack_from('<I',b,o)[0]
H=lambda b,o:struct.unpack_from('<H',b,o)[0]
def leb(b,o):
 v=0;s=0
 while True:
  x=b[o];o+=1;v|=(x&127)<<s;s+=7
  if x<128:return v,o
class Dex:
 def __init__(self,b):
  self.b=b
  self.ss=[]
  for i in range(U(b,56)):
   o=U(b,U(b,60)+4*i);_,o=leb(b,o);self.ss.append(b[o:b.index(0,o)].decode('utf8','replace'))
  self.ts=[self.ss[U(b,U(b,68)+4*i)] for i in range(U(b,64))]
  self.ms=[];self.fs=[];self.ps=[]
  for i in range(U(b,72)):
   o=U(b,76)+12*i;r=U(b,o+4);p=U(b,o+8);self.ps.append('('+''.join(self.ts[H(b,p+4+2*j)] for j in range(U(b,p)))+')'+self.ts[r] if p else '()'+self.ts[r])
  for i in range(U(b,88)):
   o=U(b,92)+8*i;self.ms.append(self.ts[H(b,o)]+'->'+self.ss[U(b,o+4)]+self.ps[H(b,o+2)])
  for i in range(U(b,80)):
   o=U(b,84)+8*i;self.fs.append(self.ts[H(b,o)]+'->'+self.ss[U(b,o+4)]+':'+self.ts[H(b,o+2)])
 def classes(self):
  b=self.b
  for i in range(U(b,96)):
   o=U(b,100)+32*i;typ=self.ts[U(b,o)];p=U(b,o+24)
   if not p:continue
   sizes=[]
   for j in range(4):n,p=leb(b,p);sizes.append(n)
   fields=[];methods=[]
   for n in sizes[:2]:
    idx=0
    for j in range(n): d,p=leb(b,p);idx+=d;f,p=leb(b,p);fields.append((idx,f))
   for n in sizes[2:]:
    idx=0
    for j in range(n):d,p=leb(b,p);idx+=d;f,p=leb(b,p);c,p=leb(b,p);methods.append((idx,f,c))
   yield typ,fields,methods
 def code(self,c):
  if not c:return []
  a=array('H');a.frombytes(self.b[c+16:c+16+2*U(self.b,c+12)]);return a
 def insns(self,c):
  a=self.code(c);i=0
  while i<len(a):
   v=a[i];op=v&255;w=1;ref=''
   if op==0 and v>>8:
    kind=v>>8
    if kind==1:w=4+2*a[i+1]
    elif kind==2:w=2+4*a[i+1]
    elif kind==3:w=4+(a[i+1]*(a[i+2]+65536*a[i+3])+1)//2
   elif op in (0x03,0x06,0x09,0x14,0x17,0x1b,0x24,0x25,0x26,0x2a,0x2b,0x2c) or 0x6e<=op<=0x72 or 0x74<=op<=0x78 or op in (0xfc,0xfd):w=3
   elif op in (0xfa,0xfb):w=4
   elif op==0x18:w=5
   elif op in (0x02,0x05,0x08,0x13,0x15,0x16,0x19,0x1a,0x1c,0x1f,0x20,0x22,0x23,0x29,0xfe,0xff) or 0x2d<=op<=0x3d or 0x44<=op<=0x6d or 0x90<=op<=0xaf or 0xd0<=op<=0xe2:w=2
   if i+w>len(a):break
   if op==0x1a:ref=self.ss[a[i+1]]
   elif op==0x1b:ref=self.ss[a[i+1]+65536*a[i+2]]
   elif 0x52<=op<=0x6d:ref=self.fs[a[i+1]]
   elif 0x6e<=op<=0x72 or 0x74<=op<=0x78:ref=self.ms[a[i+1]]
   elif op in (0x1c,0x1f,0x20,0x22,0x23):ref=self.ts[a[i+1]]
   yield i,op,ref,' '.join('%04x'%x for x in a[i:i+w]);i+=w

if __name__ == '__main__':
 if len(sys.argv)!=2: raise SystemExit('usage: python3 scripts/check-native-dex.py ORIGINAL.apk')
 wanted={'Lcmp;','Lhdp;','Ljnh;','Lanv;','Lqp;','Lqn;','Laofi;','Lqut;','Lqus;','Lcaq;','Lbsa;','Laobo;','Lckp;','Lbrv;','Lbpf;','Lbnk;','Lbnl;'}
 methods={}; fields=set(); body={}; classes=set()
 with zipfile.ZipFile(sys.argv[1]) as z:
  for name in z.namelist():
   if not name.endswith('.dex'):continue
   d=Dex(z.read(name))
   for t,fs,ms in d.classes():
    if t not in wanted:continue
    classes.add(t)
    fields.update(d.fs[i] for i,f in fs)
    for i,f,c in ms:
     m=d.ms[i]; methods[m]=(f,H(d.b,c) if c else 0)
     body[m]=list(d.insns(c))
 assert wanted<=classes, wanted-classes
 required_methods=['Lanv;->f(Lbmh;FFI)Lbmh;', 'Lqp;->f(Lbmh;FFFF)Lbmh;', 'Lqn;->c(Lbmh;Laofi;)Lbmh;', 'Laofi;->a(Ljava/lang/Object;)Ljava/lang/Object;', 'Lqut;->b()Z', 'Lcaq;->m()J','Lcaq;->a()F','Lcaq;->y(JJJJLsp;F)V','Lckp;-><init>(I)V','Lcaq;->o()Lbrv;','Lbrv;->b()Lbpu;']
 for m in required_methods:assert m in methods,m
 for f in ['Lqut;->b:Lqus;','Lqus;->f:I','Lbsa;->a:Lbsa;','Laobo;->a:Laobo;','Lbpf;->a:Landroid/graphics/Canvas;']:assert f in fields,f
 render='Lcmp;->v(Lqut;ZLkbn;Lciu;Lbmh;ZLbbj;I)V'
 assert methods[render][1]==51
 assert sum(any(x[2]==render for x in v) for k,v in body.items() if k.startswith('Lhdp;->'))==1
 code=body[render]
 def unique(ref):
  hits=[i for i,x in enumerate(code) if x[2]==ref]
  assert len(hits)==1,(ref,hits)
  return hits[0]
 row=unique('Lnnh;->b(Lbmh;Lrqu;)Lbmh;')
 icon=unique('Lanv;->c(Lbmh;F)Lbmh;')
 draw=unique('Lskk;->g(Lbsm;Ljava/lang/String;Lbmh;JLbbj;II)V')
 text=unique('Lcom/google/android/libraries/mdi/search/g3appsearch/app/$$__AppSearch__G3AttributionInfo;->c(Ljava/lang/String;Lbmh;JJLckp;JLcmf;JIZIILaofi;Lciu;Lbbj;III)V')
 assert row<icon<draw<text
 assert code[row][3].split()[-1]=='006e' and code[row+1][3]=='060c'
 assert any(x[2]=='Lqut;->c:Ljava/lang/String;' for x in code[row-12:row])
 assert code[icon-2][3]=='0615 41c0' and code[icon][3].split()[-1]=='006e'
 assert code[text][3].split()[0]=='1677' and code[text][3].split()[-1]=='0003'
 assert any(x[3]=='1814 affa 0001' for x in code[text-48:text])
 # Direct branch targets, which would skip instructions inserted before the target.
 targets=set()
 def signed(v,bits):return v-(1<<bits) if v&(1<<(bits-1)) else v
 for off,op,ref,raw in code:
  a=[int(x,16) for x in raw.split()]
  if op==0x28:targets.add(off+signed(a[0]>>8,8))
  elif op==0x29 or 0x32<=op<=0x3d:targets.add(off+signed(a[1],16))
  elif op==0x2a:targets.add(off+signed(a[1]|a[2]<<16,32))
  elif op in (0x2b,0x2c):
   payload=off+signed(a[1]|a[2]<<16,32)
   entry=next((r for r in code if r[0]==payload),None)
   if entry:
    q=[int(x,16) for x in entry[3].split()]; count=q[1]; start=4 if op==0x2b else 2+count*2
    for i in range(count):targets.add(off+signed(q[start+2*i]|q[start+2*i+1]<<16,32))
 assert not any(code[i][0] in targets for i in (row+2,icon,text)), 'injection point is a branch target'
 for m in ['Ljnh;->f(Ljnk;Z)V','Ljnh;->g(Ljnk;Z)V']:
  c=body[m]; assert any(x[2]=='Lmjy;->b()V' for x in c); assert any(x[2]=='Lsdh;->G(Z)V' for x in c)
  # Existing native notification is gated by equality and by the passed boolean.
  assert any(x[3]=='3132 002d' for x in c) and any(x[3]=='0738 002b' for x in c)
 assert any(x[2]=='Lcaq;->r()V' for x in body['Lbnl;->jM(Lcaq;)V'])
 print('PASS: original APK native row, register anchors, forecast fields, draw scope, text and color refresh anchors')
