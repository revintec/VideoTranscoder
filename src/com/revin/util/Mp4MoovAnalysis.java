package com.revin.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by revin on Jan.12,2015.
 */
public class Mp4MoovAnalysis{
    private static final Set<Class> basicTypes=new HashSet<>();
    static{
        basicTypes.add(byte.class);
        basicTypes.add(short.class);
        basicTypes.add(int.class);
        basicTypes.add(long.class);
        basicTypes.add(float.class);
        basicTypes.add(double.class);
        basicTypes.add(boolean.class);
        basicTypes.add(char.class);
        basicTypes.add(Byte.class);
        basicTypes.add(Short.class);
        basicTypes.add(Integer.class);
        basicTypes.add(Long.class);
        basicTypes.add(Float.class);
        basicTypes.add(Double.class);
        basicTypes.add(Boolean.class);
        basicTypes.add(Character.class);
        basicTypes.add(String.class);
    }
    public static String deepToString(Object o){
        try{
            StringBuilder sb=new StringBuilder();
            deepToString(new HashSet<>(),sb,"",o);
            return sb.toString();
        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }
    protected static void deepToString(Set<Object> seen,StringBuilder sb,String prefix,Object o)throws Exception{
        if(o!=null){
            Class c=o.getClass();
            if(!basicTypes.contains(c)){
                if(seen.add(o)){
                    sb.append(o).append("\n");
                    if(c.isArray()){
                        prefix+="  ";
                        int len=Array.getLength(o);
                        for(int i=0;i<len;++i){
                            sb.append(prefix);
                            deepToString(seen,sb,prefix,Array.get(o,i));
                            sb.append("\n");
                        }
                    }else{
                        prefix+="    ";
                        Field[]fields=c.getDeclaredFields();
                        for(Field f:fields){
                            sb.append(prefix);
                            f.setAccessible(true);
                            if(!Modifier.isPublic(f.getModifiers()))
                                sb.append("*");
                            sb.append(f.getName());
                            Class t=f.getType();
                            sb.append(t.isArray()?"[]: ":": ");
                            deepToString(seen,sb,prefix,f.get(o));
                            sb.append("\n");
                        }
                    }sb.setLength(sb.length()-1);
                }else sb.append(o);
            }else sb.append(o);
        }else sb.append("null");
    }
    public static class Atom{
        public final long size;
        public final String type;
        public Atom(long size,String type){
            this.size=size;
            this.type=type;
        }
    }
    public static Atom nextAtom(RandomAccessFile raf)throws IOException{
        int size=raf.readInt();
        if(size==0)return null;
        if(size<4)throw new RuntimeException("checked: size="+size);
        char[]cb=new char[4];
        for(int i=0;i<cb.length;++i){
            int v=raf.read();
            if(v<=0)throw new EOFException("read()="+v);
            cb[i]=(char)v;
        }
        // FIXME some error here
        int skip=size-4,skx=raf.skipBytes(skip);
        if(skx!=skip)
            throw new EOFException("skip("+skip+")="+skx);
        return new Atom(size,new String(cb));
    }
    public static void main(String[]args)throws IOException{
        String file="/Users/revin/Desktop/SOE-768/[PRESTIGE] ABS-223 水咲ローラ、満足度満点新人ソープ - Rola Misaki 水咲ローラ (Rola Takizawa 滝澤ローラ) 20歲 T172cmB83W58H84 (2013.05.01).mp4";
        RandomAccessFile raf=new RandomAccessFile(file,"r");
        Atom atom;
        while((atom=nextAtom(raf))!=null){
            System.out.println(deepToString(atom));
        }
    }
}
