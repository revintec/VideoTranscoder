//package com.revin.util;
//
//import org.jetbrains.annotations.Nullable;
//
//import java.io.*;
//import java.nio.BufferOverflowException;
//import java.nio.file.*;
//import java.nio.file.attribute.BasicFileAttributes;
//import java.rmi.UnexpectedException;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
///**
// *
// * Created by revin on Nov.25,2014.
// */
//public class Mp4Transcoder{
//  public static final String tmpFileAppendix=".transcoding";
//  public static class DiscardStream extends Thread{
//    protected final InputStream ins;
//    public DiscardStream(InputStream ins){this.ins=ins;}
//    @Override
//    public void run(){
//      try{
//        byte[]buffer=new byte[8000];
//        //noinspection StatementWithEmptyBody
//        while(ins.read(buffer)!=-1);
//      }catch(IOException e){e.printStackTrace();}
//      finally{try{ins.close();}catch(IOException e){e.printStackTrace();}}
//    }
//  }
//  public static class VideoInfo{
//    public final String filepath;
//    public final int duration;
//    public final int width,height;
//    public final long filesize;
//    public final String format;
//    public final int streams;
//    public VideoInfo(String filepath,int duration,int width,int height,long filesize,String format,int streams){
//      this.filepath=filepath;
//      this.duration=duration;
//      this.width=width;
//      this.height=height;
//      this.filesize=filesize;
//      this.format=format;
//      this.streams=streams;
//    }
//    @Override public String toString(){return String.format("%s%n%ss(%s %dx%d) %s",filepath,duration,format,width,height,formatSize(filesize));}
//  }
//  public static class VideoFileInfo extends VideoInfo{
//    public final String hash;
//    public VideoFileInfo(VideoInfo vi,String hash){super(vi.filepath,vi.duration,vi.width,vi.height,vi.filesize,vi.format,vi.streams);this.hash=hash;}
//    @Override public String toString(){return super.toString()+" #hash "+hash;}
//  }
//  /** IOException in this class will throw RuntimeException */
//  public static class Database{
//    public final String underlyingFile;
//    protected final Map<String,String>map=new HashMap<>();
//    public Database(String underlyingFile){
//      if(Files.isRegularFile(Paths.get(underlyingFile)))
//        fromFile(underlyingFile);
//      this.underlyingFile=underlyingFile;
//    }
//    protected void fromFile(String file){
//      try(BufferedReader br=new BufferedReader(new FileReader(file))){
//        String line;
//        while((line=br.readLine())!=null){
//          int i=line.indexOf('|');
//          if(i<=0||i>=line.length()-1)continue;
//          String key=line.substring(0,i);
//          String val=line.substring(i+1);
//          putDescription(key,val);
//        }
//      }catch(Exception e){throw new RuntimeException(e);}
//    }
//    protected void addRecord(Writer wr,String key,String val)throws IOException{wr.write(key+"|"+val+"\n");}
//    public void toFile(String file){
//      String tmpFile=file+".tmp";
//      try(Writer wr=new FileWriter(tmpFile)){
//        for(Map.Entry<String,String>e:map.entrySet())
//          addRecord(wr,e.getKey(),e.getValue());
//      }catch(Exception e){throw new RuntimeException(e);}
//      try{Files.move(Paths.get(tmpFile),Paths.get(file),StandardCopyOption.REPLACE_EXISTING);}
//      catch(Exception e){throw new RuntimeException(e);}
//    }
//    @Nullable
//    public String getDescription(String key){return map.get(key);}
//    protected void putDescription(String key,String val){
//      String old=map.put(key,val);
//      if(old!=null)map.put(key,old+"; "+val);
//    }
//    public void putDescAndFlush(String key,Object val){
//      String d=""+val;
//      putDescription(key,d);
//      try(Writer wr=new FileWriter(underlyingFile,true)){
//        addRecord(wr,key,d);
//      }catch(Exception e){throw new RuntimeException(e);}
//    }
//  }
//  public static enum Reason{
//    FILE_EITHER_TOO_LARGE_OR_TOO_SMALL,
//    VIDEO_DURATION_TOO_LONG,
//    VIDEO_RESOLUTION_ALREADY_LOW,
//    VIDEO_FORMAT_ALREADY_MP4,
//    NOT_SHRINKING_ENOUGH_AFTER_CONV,MAY_CONTAIN_OTHER_STREAM,NOT_VIDEO_OR_CANT_GET_INFO
//  }
//  protected static final Database db=new Database("transcoder.db");
////  private static final char[] b2sDict="0123456789abcdefg".toCharArray();
////  public static String bytes2String(byte[]bytes){String sb="";for(byte b:bytes)sb+=b2sDict[b>>4&0xf]+b2sDict[b&0xf];return sb;}
////  @Nullable
////  public static String getFileHash(String path){
////    MessageDigest md;
////    try{md=MessageDigest.getInstance("SHA-1");}
////    catch(NoSuchAlgorithmException e){throw new RuntimeException(e);}
////    try(InputStream ins=new FileInputStream(path)){
////      byte[]buffer=new byte[1048576];
////      while(true){
////        int len=ins.read(buffer);
////        if(len==0)throw new UnexpectedException("read() returns 0");
////        else if(len==-1)break;
////        else md.update(buffer,0,len);
////      }
////    }catch(Exception e){return null;}
////    return bytes2String(md.digest());
////  }
//  public static String getFileName(String path){return ""+Paths.get(path).getFileName();}
//  public static String formatSize(long size){
//    if(size<0)throw new IllegalArgumentException("size can't be negative");
//    else if(size<1e3)return (int)(size/1e0)+" B";
//    else if(size<1e6)return (int)(size/1e3)+" K";
//    else if(size<1e9)return (int)(size/1e6)+" M";
//    else return (int)(size/1e9)+" G";
//  }
//  protected static final Pattern
//    ptDuration=Pattern.compile("format\\.duration=\"([\\d\\.]+)\""),
//    ptWidth=Pattern.compile("streams\\.stream\\.\\d+\\.width=(\\d+)"),
//    ptHeight=Pattern.compile("streams\\.stream\\.\\d+\\.height=(\\d+)"),
//    ptFormat=Pattern.compile("format\\.format_name=\"(.+)\""),
//    ptStreams=Pattern.compile("format\\.nb_streams=(\\d+)");
//  @Nullable
//  public static String matchAgainst(Pattern pt,String text){
//    Matcher mt=pt.matcher(text);
//    if(!mt.find())return null;
//    String data=mt.group(1);
//    if(mt.find()||data.trim().isEmpty())
//      return null;
//    else return data;
//  }
//  protected static Runtime runtime=Runtime.getRuntime();
//  public static final String ffprobe="ffprobe";
//  public static final String ffmpeg="ffmpeg";
//  @Nullable
//  public static VideoInfo getInfo(String path){
//    Process process=null;
//    try{
//      Path p=Paths.get(path);
//      long fileSize=Files.size(p);
//      process=runtime.exec(new String[]{ffprobe,"-v","quiet","-of","flat","-show_streams","-show_format",path});
//      byte[]buffer=new byte[1048576];int len=0;
//      InputStream ins=process.getInputStream();
//      new DiscardStream(process.getErrorStream()).start();
//      while(true){
//        int l=ins.read(buffer,len,buffer.length-len);
//        if(l==0)throw new BufferOverflowException();
//        else if(l==-1)break;
//        else len+=l;
//      }process.destroy();process=null;
//      String output=new String(buffer,0,len);
//      String duration=matchAgainst(ptDuration,output);
//      String width=matchAgainst(ptWidth,output);
//      String height=matchAgainst(ptHeight,output);
//      String format=matchAgainst(ptFormat,output);
//      String streams=matchAgainst(ptStreams,output);
//      if(duration==null||width==null||height==null||format==null||streams==null)return null;
//      return new VideoInfo(path,(int)Double.parseDouble(duration),Integer.parseInt(width),Integer.parseInt(height),fileSize,format,Integer.parseInt(streams));
//    }catch(Exception e){
//      if(process!=null)
//        process.destroy();
//      return null;
//    }
//  }
//  @Nullable
//  public static Reason shouldEncode(VideoInfo vi){
//    if(vi==null)return Reason.NOT_VIDEO_OR_CANT_GET_INFO;
//    if(vi.streams!=2)return Reason.MAY_CONTAIN_OTHER_STREAM;
//    if(vi.filesize<104857600/*||vi.filesize>Integer.MAX_VALUE*/)
//      return Reason.FILE_EITHER_TOO_LARGE_OR_TOO_SMALL;
//    if(vi.duration>10000)return Reason.VIDEO_DURATION_TOO_LONG;
//    if(vi.width+vi.height<1600)return Reason.VIDEO_RESOLUTION_ALREADY_LOW;
//    if(vi.format.contains("mp4"))return Reason.VIDEO_FORMAT_ALREADY_MP4;
//    return null;
//  }
//  protected static final Pattern ptStat=Pattern.compile("fps=\\s*(\\d+).*time=([\\d:\\.]+)");
//  @Nullable
//  public static String transcodeWithSoutPrints(VideoInfo vi){
//    String tmp=vi.filepath+tmpFileAppendix;
//    Path tp=Paths.get(tmp);
//    if(Files.isRegularFile(tp))return null;
//    Process process=null;
//    try{
//      int width=Math.min(800,vi.width);
//      int height=(int)((double)vi.height/vi.width*width);
//      process=runtime.exec(new String[]{
//          ffmpeg,"-i",vi.filepath,"-f","mp4","-s",width+"x"+height,"-n",tmp});
//      new DiscardStream(process.getInputStream()).start();
//      BufferedReader br=new BufferedReader(new InputStreamReader(process.getErrorStream()));
//      String line,blank="                                ";
//      String wholeBuffer="";
//      System.out.println(vi.filepath);
//      while((line=br.readLine())!=null){
//        Matcher mt=ptStat.matcher(line);
//        if(!mt.find()){wholeBuffer+=line+"\n";continue;}
//        int fps=Integer.parseInt(mt.group(1));
//        String time=mt.group(2);
//        String out=String.format("\r%s\r%2d%%, FPS=%d, time=%s",blank,timecode(time)*100/vi.duration,fps,time);
//        System.out.print(out);
//      }System.out.format("\r%s\r",blank);
//      int exitCode;
//      if((exitCode=process.exitValue())!=0){
//        System.err.println(wholeBuffer);
//        throw new UnexpectedException("exit code: "+exitCode);
//      }return tmp;
//    }catch(Exception e){
//      e.printStackTrace();
//      if(process!=null)
//        process.destroy();
//      deleteFile(tp);
//      return null;
//    }
//  }
//  private static boolean replaceFile(String src,String dst){
//    try{Files.move(Paths.get(src),Paths.get(dst),StandardCopyOption.REPLACE_EXISTING);return true;}
//    catch(IOException e){e.printStackTrace();return false;}
//  }
//  private static boolean tryMoveFile(String src,String dst){
//    try{Files.move(Paths.get(src),Paths.get(dst));return true;}
//    catch(IOException e){return false;}
//  }
//  private static boolean deleteFile(String file){return deleteFile(Paths.get(file));}
//  private static boolean deleteFile(Path file){try{Files.delete(file);return true;}catch(IOException e){e.printStackTrace();return false;}}
//  private static String getNewFilePathPre(String path){
//    Path p=Paths.get(path);
//    String filename=p.getFileName().toString();
//    int i=filename.lastIndexOf('.');
//    if(i<=0||filename.length()-i>5) // .rmvb
//      return path;
//    return p.resolveSibling(filename.substring(0,i)).toString();
//  }
//  public static void finishWithFileAndDbOps(VideoFileInfo vi,String tmp){
//    VideoInfo nvi=getInfo(tmp);
//    if(nvi==null||nvi.filesize-vi.filesize>-104857600){
//      deleteFile(tmp);
//      db.putDescAndFlush(vi.hash,Reason.NOT_SHRINKING_ENOUGH_AFTER_CONV);
//    }else{
//      String newFilePathPre=getNewFilePathPre(vi.filepath);
//      if(!(newFilePathPre+".mp4").equals(vi.filepath)){
//        if(!tryMoveFile(nvi.filepath,newFilePathPre+".mp4")){
//          int i=1;
//          while(!tryMoveFile(nvi.filepath,newFilePathPre+"("+i+").mp4"))
//            ++i;
//          newFilePathPre+="("+i+")";
//        }deleteFile(vi.filepath);
//      }else replaceFile(nvi.filepath,vi.filepath);
//      String output=String.format("%s -> %s %s",formatSize(vi.filesize),formatSize(nvi.filesize),newFilePathPre+".mp4");
//      System.out.println(output);
////      db.putDescAndFlush(vi.hash,output);
//    }
//  }
//  protected static final Pattern ptTime=Pattern.compile("(\\d+):(\\d+):(\\d+)\\.(\\d+)");
//  public static int timecode(String time){
//    Matcher ma=ptTime.matcher(time);
//    if(!ma.matches())throw new IllegalArgumentException(time);
//    return Integer.parseInt(ma.group(1))*3600+Integer.parseInt(ma.group(2))*60+Integer.parseInt(ma.group(3));
//  }
//  public static void main(String[]args)throws IOException{
//    if(args.length!=1){
//      System.err.println("usage: mp4transcoder path/to/video/root/folder");
//      return;
//    }
//    Path path=Paths.get(args[0]);
//    Files.walkFileTree(path,new FVCleanup());
//    Files.walkFileTree(path,new FVDoConv());
//  }
//  public static class FVCleanup implements FileVisitor<Path>{
//    @Override public FileVisitResult preVisitDirectory(Path p,BasicFileAttributes a){return FileVisitResult.CONTINUE;}
//    @Override public FileVisitResult visitFile(Path p,BasicFileAttributes a){
//      if(p.toString().endsWith(tmpFileAppendix))
//        deleteFile(p);
//      return FileVisitResult.CONTINUE;
//    }
//    @Override public FileVisitResult visitFileFailed(Path p,IOException e){return FileVisitResult.CONTINUE;}
//    @Override public FileVisitResult postVisitDirectory(Path p,IOException e){return FileVisitResult.CONTINUE;}
//  }
//  public static class FVDoConv implements FileVisitor<Path>{
//    @Override public FileVisitResult preVisitDirectory(Path p,BasicFileAttributes a){return FileVisitResult.CONTINUE;}
//    @Override public FileVisitResult visitFile(Path p,BasicFileAttributes a){
//      if(a.size()<10485760)return FileVisitResult.CONTINUE;
//      String path=""+p;Object reason;
//      //noinspection LoopStatementThatDoesntLoop
//      do{
//        VideoInfo vi=getInfo(path);
//        reason=shouldEncode(vi);
//        if(reason!=null)break;
//        String filehash=getFileName(path);
//        reason=db.getDescription(filehash);
//        if(reason!=null)break;
//        String tmp=transcodeWithSoutPrints(vi);
//        if(tmp==null){
//          reason="TRANSCODING_ERROR";
//          break;
//        }finishWithFileAndDbOps(new VideoFileInfo(vi,filehash),tmp);
//        return FileVisitResult.CONTINUE;
//      }while(false);
//      System.out.println(reason+"  "+path);
//      return FileVisitResult.CONTINUE;
//    }
//    @Override public FileVisitResult visitFileFailed(Path p,IOException e){return FileVisitResult.CONTINUE;}
//    @Override public FileVisitResult postVisitDirectory(Path p,IOException e){return FileVisitResult.CONTINUE;}
//  }
//}
