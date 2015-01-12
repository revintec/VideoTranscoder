package com.revin.util;

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
}
