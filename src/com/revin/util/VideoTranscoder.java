package com.revin.util;

import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.BufferOverflowException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.rmi.UnexpectedException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * Created by revin on Nov.25,2014.
 */
public class VideoTranscoder{
  public static final String tmpFileAppendix=".transcoding";
  public static class DiscardStream extends Thread{
    protected final InputStream ins;
    public DiscardStream(InputStream ins){this.ins=ins;}
    @Override
    public void run(){
      try{
        byte[]buffer=new byte[8000];
        //noinspection StatementWithEmptyBody
        while(ins.read(buffer)!=-1);
      }catch(IOException e){e.printStackTrace();}
      finally{try{ins.close();}catch(IOException e){e.printStackTrace();}}
    }
  }
  public static class VideoInfo{
    public final String filepath;
    // used to determine is transcode necessary
    public final boolean format;
    public final boolean otherStreams;
    // video profile, audio profile
    public final boolean vpro,apro;
    public final int duration;     // used to display percent completed
    public final int fps;          // used to display transcode speed
    public final int width,height; // used to calculate scale ONLY
    public final long filesize;    // used to print how many disk-space was saved
    public VideoInfo(String filepath,boolean format,boolean otherStreams,boolean vpro,boolean apro,int duration,int fps,int width,int height,long filesize){
      this.filepath=filepath;
      this.format=format;
      this.otherStreams=otherStreams;
      this.vpro=vpro;
      this.apro=apro;
      this.duration=duration;
      this.fps=fps;
      this.width=width;
      this.height=height;
      this.filesize=filesize;
    }
    @Override public String toString(){return String.format("%s%n%ss(%dx%d) %s",filepath,duration,width,height,formatSize(filesize));}
  }
  public static String formatSize(long size){
    if(size<0)throw new IllegalArgumentException("size can't be negative");
    else if(size<1e3)return (int)(size/1e0)+" B";
    else if(size<1e6)return (int)(size/1e3)+" K";
    else if(size<1e9)return (int)(size/1e6)+" M";
    else return (int)(size/1e9)+" G";
  }
  /** @param time in seconds */
  public static String formatTimeX(int time){
    if(time<0)throw new IllegalArgumentException("time can't be negative");
    int h=time/3600;
    int m=time/60%60;
    int s=time%60;
    return String.format("%02d:%02d:%02d",h,m,s);
  }
  @Nullable
  public static String getProperty(String[]output,String prop){
    String prefix=prop+"=";
    String data=null;
    for(String line:output){
      if(!line.startsWith(prefix))
        continue;
      if(data!=null)return null;// should have only one match
      line=line.substring(prefix.length());
      if(line.startsWith("\"")&&line.endsWith("\""))
        data=line.substring(1,line.length()-1);
      else data=line;
    }return data;
  }
  @Nullable
  public static String getProperty(String[]output,int streamid,String prop){return getProperty(output,"streams.stream."+streamid+"."+prop);}
  protected static Runtime runtime=Runtime.getRuntime();
  private static int index(String[]ar,String only){
    int index=-1;
    for(int i=0;i<ar.length;++i){
      if(only.equals(ar[i])){
        if(index!=-1)
          throw new IllegalArgumentException("more than one occurrence");
        else index=i;
      }
    }if(index==-1)
      throw new IllegalArgumentException("no occurrence");
    else return index;
  }
  protected static final Pattern ptFps=Pattern.compile("(\\d+)/(\\d+)");
  public static final String[]ffprobe="ffprobe -v quiet -of flat -show_streams -show_format -i PATH_IN".split("\\s+");
  public static final int ffprobeIN=index(ffprobe,"PATH_IN");
  /** @return null indicates not a video file or ffprobe returns incomplete information */
  @Nullable
  public static VideoInfo getInfo(String path){
    Process process=null;
    try{
      Path p=Paths.get(path);
      long filesize=Files.size(p);
      ffprobe[ffprobeIN]=path;
      process=runtime.exec(ffprobe);
      byte[]buffer=new byte[1048576];int len=0;
      InputStream ins=process.getInputStream();
      new DiscardStream(process.getErrorStream()).start();
      while(true){
        int l=ins.read(buffer,len,buffer.length-len);
        if(l==0)throw new BufferOverflowException();
        else if(l==-1)break;
        else len+=l;
      }process.waitFor(3000,TimeUnit.MILLISECONDS);
      int exitCode;
      if((exitCode=process.exitValue())!=0){
        if(len>0)System.err.println(new String(buffer,0,len));
        throw new UnexpectedException("exit code: "+exitCode);
      }
      String[]output=new String(buffer,0,len).split("\r?\n");
      //noinspection ConstantConditions
      boolean format=getProperty(output,"format.format_name").matches(".*\\bmp4\\b.*");
      //noinspection ConstantConditions
      int duration=(int)Double.parseDouble(getProperty(output,"format.duration"));
      //noinspection ConstantConditions
      int streams=Integer.parseInt(getProperty(output,"format.nb_streams"));
      boolean otherStreams=false;
      int vpro=0,apro=0;
      int width=0,height=0,fps=0;
      for(int i=0;i<streams;++i){
        String data=getProperty(output,i,"codec_type");
        if(data==null)return null;
        switch(data){
          case "audio":
            if(apro>=0){
              if("aac".equals(getProperty(output,i,"codec_name")))
                apro=1;
              else apro=-1;
//              if(!"aac_he_v2".equals(getProperty(output,i,"profile")))profile=false;
            }break;
          case "video":
            if(vpro>=0){
              if("h264".equals(getProperty(output,i,"codec_name")))
                vpro=1;
              else vpro=-1;
//            if(!"Main".equals(getProperty(output,i,"profile")))profile=false;
            }
            //noinspection ConstantConditions
            Matcher m=ptFps.matcher(getProperty(output,i,"avg_frame_rate"));
            if(m.find()){
              int k=Integer.parseInt(m.group(1)),v=Integer.parseInt(m.group(2));
              if(!m.find()){
                int fpx=(int)Math.ceil((double)k/v);
                if(fpx>fps)fps=fpx;
              }
            }
            //noinspection ConstantConditions
            int w=Integer.parseInt(getProperty(output,i,"width"));
            //noinspection ConstantConditions
            int h=Integer.parseInt(getProperty(output,i,"height"));
            int pixels=w*h;
            if(pixels>width*height){
              if(pixels>800*800)
                vpro=-1;
              width=w;
              height=h;
            }break;
          default:otherStreams=true;
        }
      }if(vpro==0||apro==0)return null;
      return new VideoInfo(path,format,otherStreams,vpro>0,apro>0,duration,fps,width,height,filesize);
    }catch(Exception e){
      e.printStackTrace();
      return null;
    }finally{
      if(process!=null)
        process.destroyForcibly();
    }
  }
  /** @return null means should encode */
  public static String shouldEncode(VideoInfo vi){
    if(vi==null)return "INCOMPLETE_VIDEOINFO";
    if(vi.otherStreams)return "OTHER_STREAMS_PRESENT";
    // vi.width, vi.height already processed
    // in getInfo(...), if(pixels>800*800)vpro=-1;
    if(vi.format&&vi.vpro&&vi.apro)
      return "PROFILE_MATCH";
    return null; // means should convert
  }
  protected static final Pattern ptStat=Pattern.compile("fps=\\s*(\\d+).*time=([\\d:\\.]+)");
  protected static final Pattern ptTime=Pattern.compile("(\\d+):(\\d+):(\\d+)\\.(\\d+)");
  public static final String[]ffmpeg="ffmpeg -i PATH_IN -n -f mp4 -vcodec VCODEC -s SCALE -acodec ACODEC -profile:a aac_he_v2 PATH_OUT".split("\\s+");
  public static final int ffmpegVcodec=index(ffmpeg,"VCODEC"),ffmpegAcodec=index(ffmpeg,"ACODEC");
  public static final int ffmpegIN=index(ffmpeg,"PATH_IN"),ffmpegSCALE=index(ffmpeg,"SCALE"),ffmpegOUT=index(ffmpeg,"PATH_OUT");
  public static final String transcodedPostfix=".mp4";
  public static String speedFactor(VideoInfo vi,int fps){
    if(vi.fps>0)
      return "("+Math.round((double)fps/vi.fps)+"x)";
    else return "";
  }
  /** timestamp in seconds */
  private static long timeStamp(){return System.nanoTime()/1000000000;}
  @Nullable
  public static String transcodeWithSoutPrints(VideoInfo vi){
    String tmp=vi.filepath+tmpFileAppendix;
    Process process=null;boolean clean=false;
    try{
      ffmpeg[ffmpegVcodec]=vi.vpro?"copy":"libx264";
      ffmpeg[ffmpegAcodec]=vi.apro?"copy":"libfdk_aac";
      int width=Math.min(800,vi.width);
      int height=(int)Math.round((double)vi.height/vi.width*width);
      ffmpeg[ffmpegIN]=vi.filepath;
      ffmpeg[ffmpegSCALE]=width+"x"+height;
      ffmpeg[ffmpegOUT]=tmp;
      process=runtime.exec(ffmpeg);
      new DiscardStream(process.getInputStream()).start();
      BufferedReader br=new BufferedReader(new InputStreamReader(process.getErrorStream()));
      String line,clear="\r                                                                \r";
      String wholeBuffer="";
      String diff=String.format("F%d V%d A%d O%d",vi.format?1:0,vi.vpro?1:0,vi.apro?1:0,vi.otherStreams?1:0);
      System.out.println(diff+"    "+vi.filepath);
      long startTime=timeStamp();
      while((line=br.readLine())!=null){
        Matcher mt=ptStat.matcher(line);
        if(!mt.find()){wholeBuffer+=line+"\n";continue;}
        int fps=Integer.parseInt(mt.group(1));
        String time=mt.group(2);
        Matcher ma=ptTime.matcher(time);
        if(!ma.matches())throw new IllegalArgumentException(time);
        int timecode=Integer.parseInt(ma.group(1))*3600+Integer.parseInt(ma.group(2))*60+Integer.parseInt(ma.group(3));
        double percent=(double)timecode*100/vi.duration;
        int eta=(int)(timeStamp()-startTime);
        eta=(int)(eta*(100-percent)/percent);
        String out=String.format("\r%s\r%2d%%, FPS=%d%s, time=%s, ETA=%s",clear,(int)percent,fps,speedFactor(vi,fps),time,formatTimeX(eta));
        System.out.print(out);
      }process.waitFor(3000,TimeUnit.MILLISECONDS);
      System.out.print(clear);clean=true;
      int exitCode;
      if((exitCode=process.exitValue())!=0){
        System.err.println(wholeBuffer);
        throw new UnexpectedException("exit code: "+exitCode);
      }return tmp;
    }catch(Exception e){
      if(!clean)System.err.println();
      e.printStackTrace();
      deleteFile(tmp);
      return null;
    }finally{
      if(process!=null)
        process.destroyForcibly();
    }
  }
  private static boolean replaceFile(String src,String dst){
    try{Files.move(Paths.get(src),Paths.get(dst),StandardCopyOption.REPLACE_EXISTING);return true;}
    catch(IOException e){e.printStackTrace();return false;}
  }
  private static boolean tryMoveFile(String src,String dst){
    try{Files.move(Paths.get(src),Paths.get(dst));return true;}
    catch(IOException e){return false;}
  }
  private static boolean deleteFile(String file){return deleteFile(Paths.get(file));}
  private static boolean deleteFile(Path file){try{Files.delete(file);return true;}catch(IOException e){e.printStackTrace();return false;}}
  private static String getFilePathPre(String path){
    Path p=Paths.get(path);
    String filename=p.getFileName().toString();
    int i=filename.lastIndexOf('.');
    if(i<=0||filename.length()-i>5) // .rmvb
      return path;
    return p.resolveSibling(filename.substring(0,i)).toString();
  }
  public static boolean finishWithFileAndDbOps(VideoInfo vi,String tmp){
    VideoInfo nvi=getInfo(tmp);
    if(nvi==null||!(nvi.format&&nvi.vpro&&nvi.apro)){
      deleteFile(tmp);
      return false;
    }else if(Math.abs(vi.duration-nvi.duration)>3){
      System.err.println("\rDurationMismatch: "+formatTimeX(vi.duration)+" -> "+formatTimeX(nvi.duration));
      deleteFile(tmp);
      return false;
    }String newFilePathPre=getFilePathPre(vi.filepath);
    if(!(newFilePathPre+transcodedPostfix).equalsIgnoreCase(vi.filepath)){
      if(!tryMoveFile(nvi.filepath,newFilePathPre+transcodedPostfix)){
        int i=1;
        while(!tryMoveFile(nvi.filepath,newFilePathPre+"("+i+")"+transcodedPostfix))
          ++i;
        newFilePathPre+="("+i+")";
      }deleteFile(vi.filepath);
    }else replaceFile(nvi.filepath,vi.filepath);
    String output=String.format("%s -> %s %s",formatSize(vi.filesize),formatSize(nvi.filesize),newFilePathPre+transcodedPostfix);
    System.out.println(output);
    return true;
  }
  public static void main(String[]args)throws IOException{
    if(args.length!=1){
      System.err.println("usage: videoTranscoder path/to/video/root/folder");
      return;
    }
    Path path=Paths.get(args[0]);
    Files.walkFileTree(path,new FVCleanup());
    Files.walkFileTree(path,new FVDoConv());
  }
  public static class FVCleanup implements FileVisitor<Path>{
    @Override public FileVisitResult preVisitDirectory(Path p,BasicFileAttributes a){return FileVisitResult.CONTINUE;}
    @Override public FileVisitResult visitFile(Path p,BasicFileAttributes a){
      String path=""+p;
      if(path.endsWith(tmpFileAppendix)){
        System.out.println("Deleting: "+path);
        deleteFile(p);
      }return FileVisitResult.CONTINUE;
    }
    @Override public FileVisitResult visitFileFailed(Path p,IOException e){return FileVisitResult.CONTINUE;}
    @Override public FileVisitResult postVisitDirectory(Path p,IOException e){return FileVisitResult.CONTINUE;}
  }
  public static class FVDoConv implements FileVisitor<Path>{
    @Override public FileVisitResult preVisitDirectory(Path p,BasicFileAttributes a){return FileVisitResult.CONTINUE;}
    @Override public FileVisitResult visitFile(Path p,BasicFileAttributes a){
      if(a.size()<10485760)return FileVisitResult.CONTINUE; // less than 10M is likely not a video file
      String path=""+p;
      // .DO_NOT_TRSC. can also be in the path(not just the filename)
      // and it's case sensitive
      if(path.contains(".DO_NOT_TRSC.")){
        String out=String.format("Skipping(%s):","PRAGMA_IGN");
        out=String.format("%-32s %s",out,path);
        System.out.println(out);
        return FileVisitResult.CONTINUE;
      }
      //noinspection LoopStatementThatDoesntLoop
      VideoInfo vi=getInfo(path);
      String shouldEncode=shouldEncode(vi);
      if(shouldEncode!=null){
        String out=String.format("Skipping(%s):",shouldEncode);
        out=String.format("%-32s %s",out,path);
        System.out.println(out);
        return FileVisitResult.CONTINUE;
      }
      String tmp=transcodeWithSoutPrints(vi);
      if(tmp!=null)finishWithFileAndDbOps(vi,tmp);
      return FileVisitResult.CONTINUE;
    }
    @Override public FileVisitResult visitFileFailed(Path p,IOException e){return FileVisitResult.CONTINUE;}
    @Override public FileVisitResult postVisitDirectory(Path p,IOException e){return FileVisitResult.CONTINUE;}
  }
}
