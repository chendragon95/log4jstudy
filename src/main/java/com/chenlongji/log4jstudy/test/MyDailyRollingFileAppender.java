package com.chenlongji.log4jstudy.test;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author clj
 * 自定义的DailyRollingFileAppender, 用于自动删除过期的日志
 */
public class MyDailyRollingFileAppender extends FileAppender {


  // The code assumes that the following constants are in a increasing
  // sequence.
  static final int TOP_OF_TROUBLE=-1;
  static final int TOP_OF_MINUTE = 0;
  static final int TOP_OF_HOUR   = 1;
  static final int HALF_DAY      = 2;
  static final int TOP_OF_DAY    = 3;
  static final int TOP_OF_WEEK   = 4;
  static final int TOP_OF_MONTH  = 5;

    /**
     * 最大备份文件数
     */
    private int maxBackupIndex = 60;


  /**
     The date pattern. By default, the pattern is set to
     "'.'yyyy-MM-dd" meaning daily rollover.
   */
  private String datePattern = "'.'yyyy-MM-dd";

  /**
     The log file will be renamed to the value of the
     scheduledFilename variable when the next interval is entered. For
     example, if the rollover period is one hour, the log file will be
     renamed to the value of "scheduledFilename" at the beginning of
     the next hour. 

     The precise time when a rollover occurs depends on logging
     activity. 
  */
  private String scheduledFilename;

  /**
     The next time we estimate a rollover should occur. */
  private long nextCheck = System.currentTimeMillis () - 1;

  Date now = new Date();

  SimpleDateFormat sdf;

  RollingCalendar rc = new RollingCalendar();

  int checkPeriod = TOP_OF_TROUBLE;

  // The gmtTimeZone is used only in computeCheckPeriod() method.
  static final TimeZone gmtTimeZone = TimeZone.getTimeZone("GMT");


  /**
     The default constructor does nothing. */
  public MyDailyRollingFileAppender() {
  }

  /**
    Instantiate a <code>MyDailyRollingFileAppender</code> and open the
    file designated by <code>filename</code>. The opened filename will
    become the ouput destination for this appender.

    */
  public MyDailyRollingFileAppender(Layout layout, String filename,
                                    String datePattern) throws IOException {
    super(layout, filename, true);
    this.datePattern = datePattern;
    activateOptions();
  }

  /**
     The <b>DatePattern</b> takes a string in the same format as
     expected by {@link SimpleDateFormat}. This options determines the
     rollover schedule.
   */
  public void setDatePattern(String pattern) {
    datePattern = pattern;
  }

  /** Returns the value of the <b>DatePattern</b> option. */
  public String getDatePattern() {
    return datePattern;
  }

    /**
     Set the maximum number of backup files to keep around.
     <p>The <b>MaxBackupIndex</b> option determines how many backup
     files are kept before the oldest is erased. This option takes
     a positive integer value. If set to zero, then there will be no
     backup files and the log file will be truncated when it reaches
     <code>MaxFileSize</code>.
     */
    public void setMaxBackupIndex(int maxBackups) {
        this.maxBackupIndex = maxBackups;
    }

    public int getMaxBackupIndex(){
        return maxBackupIndex;
    }

  @Override
  public void activateOptions() {
    super.activateOptions();
    if(datePattern != null && fileName != null) {
      now.setTime(System.currentTimeMillis());
      sdf = new SimpleDateFormat(datePattern);
      int type = computeCheckPeriod();
      printPeriodicity(type);
      rc.setType(type);
      File file = new File(fileName);
      scheduledFilename = fileName+sdf.format(new Date(file.lastModified()));

    } else {
      LogLog.error("Either File or DatePattern options are not set for appender ["
		   +name+"].");
    }
  }

  void printPeriodicity(int type) {
    switch(type) {
    case TOP_OF_MINUTE:
      LogLog.debug("Appender ["+name+"] to be rolled every minute.");
      break;
    case TOP_OF_HOUR:
      LogLog.debug("Appender ["+name
		   +"] to be rolled on top of every hour.");
      break;
    case HALF_DAY:
      LogLog.debug("Appender ["+name
		   +"] to be rolled at midday and midnight.");
      break;
    case TOP_OF_DAY:
      LogLog.debug("Appender ["+name
		   +"] to be rolled at midnight.");
      break;
    case TOP_OF_WEEK:
      LogLog.debug("Appender ["+name
		   +"] to be rolled at start of week.");
      break;
    case TOP_OF_MONTH:
      LogLog.debug("Appender ["+name
		   +"] to be rolled at start of every month.");
      break;
    default:
      LogLog.warn("Unknown periodicity for appender ["+name+"].");
    }
  }


  // This method computes the roll over period by looping over the
  // periods, starting with the shortest, and stopping when the r0 is
  // different from from r1, where r0 is the epoch formatted according
  // the datePattern (supplied by the user) and r1 is the
  // epoch+nextMillis(i) formatted according to datePattern. All date
  // formatting is done in GMT and not local format because the test
  // logic is based on comparisons relative to 1970-01-01 00:00:00
  // GMT (the epoch).

  int computeCheckPeriod() {
    RollingCalendar rollingCalendar = new RollingCalendar(gmtTimeZone, Locale.getDefault());
    // set sate to 1970-01-01 00:00:00 GMT
    Date epoch = new Date(0);
    if(datePattern != null) {
      for(int i = TOP_OF_MINUTE; i <= TOP_OF_MONTH; i++) {
	SimpleDateFormat simpleDateFormat = new SimpleDateFormat(datePattern);
	simpleDateFormat.setTimeZone(gmtTimeZone); // do all date formatting in GMT
	String r0 = simpleDateFormat.format(epoch);
	rollingCalendar.setType(i);
	Date next = new Date(rollingCalendar.getNextCheckMillis(epoch));
	String r1 =  simpleDateFormat.format(next);
	//System.out.println("Type = "+i+", r0 = "+r0+", r1 = "+r1);
	if(r0 != null && r1 != null && !r0.equals(r1)) {
	  return i;
	}
      }
    }
    return TOP_OF_TROUBLE; // Deliberately head for trouble...
  }

  /**
     Rollover the current file to a new file.
  */
  void rollOver() throws IOException {

    /* Compute filename, but only if datePattern is specified */
    if (datePattern == null) {
      errorHandler.error("Missing DatePattern option in rollOver().");
      return;
    }

    String datedFilename = fileName+sdf.format(now);
    // It is too early to roll over because we are still within the
    // bounds of the current interval. Rollover will occur once the
    // next interval is reached.
    if (scheduledFilename.equals(datedFilename)) {
      return;
    }

    // close current file, and rename it to datedFilename
    this.closeFile();

    File target  = new File(scheduledFilename);
    if (target.exists()) {
      target.delete();
    }

    File file = new File(fileName);
    boolean result = file.renameTo(target);
    if(result) {
      LogLog.debug(fileName +" -> "+ scheduledFilename);
    } else {
      LogLog.error("Failed to rename ["+fileName+"] to ["+scheduledFilename+"].");
    }

    // 删除文件
    removeFiles(file);

    try {
      // This will also close the file. This is OK since multiple
      // close operations are safe.
      this.setFile(fileName, true, this.bufferedIO, this.bufferSize);
    }
    catch(IOException e) {
      errorHandler.error("setFile("+fileName+", true) call failed.");
    }
    scheduledFilename = datedFilename;
  }

    /**
   * This method differentiates MyDailyRollingFileAppender from its
   * super class.
   *
   * <p>Before actually logging, this method will check whether it is
   * time to do a rollover. If it is, it will schedule the next
   * rollover time and then rollover.
   * */
  @Override
  protected void subAppend(LoggingEvent event) {
    long n = System.currentTimeMillis();
    if (n >= nextCheck) {
      now.setTime(n);
      nextCheck = rc.getNextCheckMillis(now);
      try {
	rollOver();
      }
      catch(IOException ioe) {
          if (ioe instanceof InterruptedIOException) {
              Thread.currentThread().interrupt();
          }
	      LogLog.error("rollOver() failed.", ioe);
      }
    }
    super.subAppend(event);
   }

    /**
     * 删除文件
     */
    private void removeFiles(File file) {
        //获取日志文件列表，控制数量，实现清理策略
        try{
            if (file.getParentFile().exists()){
                File[] files = file.getParentFile().listFiles(new LogFileFilter(file.getName()));
                if (files == null) {
                    return;
                }
                Long[] dateArray = new Long[files.length];
                for (int i = 0; i < files.length; i++) {
                    File fileItem = files[i];
                    String fileDateStr = fileItem.getName().replace(file.getName(), "");
                    try {
                        Date fileDate = sdf.parse(fileDateStr);
                        long fileDateLong = fileDate.getTime();
                        dateArray[i] = fileDateLong;
                    } catch (ParseException e) {
                        LogLog.error("Parse File Date Throw Exception : " + e.getMessage());
                    }
                }
                Arrays.sort(dateArray);
                if (dateArray.length > maxBackupIndex) {
                    for (int i = 0; i < dateArray.length - maxBackupIndex; i++) {
                        String dateFileName = file.getPath() + sdf.format(dateArray[i]);
                        File dateFile = new File(dateFileName);
                        Files.delete(dateFile.toPath());
                    }
                }
            }
        } catch (Exception e){
            errorHandler.error("removeFiles failed." + e.getMessage());
        }
    }

    /**
     * 文件过滤器
     */
    class LogFileFilter implements FileFilter {
        private String logName;

        public LogFileFilter(String logName) {
            this.logName = logName;
        }

        @Override
        public boolean accept(File file) {
            if (logName == null || file.isDirectory()) {
                return false;
            } else {
                LogLog.debug(file.getName());
                return file.getName().startsWith(logName);
            }
        }
    }

}

/**
 *  RollingCalendar is a helper class to MyDailyRollingFileAppender.
 *  Given a periodicity type and the current time, it computes the
 *  start of the next interval.  
 * */
class RollingCalendar extends GregorianCalendar {
  private static final long serialVersionUID = -3560331770601814177L;

  int type = MyDailyRollingFileAppender.TOP_OF_TROUBLE;

  RollingCalendar() {
    super();
  }  

  RollingCalendar(TimeZone tz, Locale locale) {
    super(tz, locale);
  }  

  void setType(int type) {
    this.type = type;
  }

  public long getNextCheckMillis(Date now) {
    return getNextCheckDate(now).getTime();
  }

  public Date getNextCheckDate(Date now) {
    this.setTime(now);

    switch(type) {
    case MyDailyRollingFileAppender.TOP_OF_MINUTE:
	this.set(Calendar.SECOND, 0);
	this.set(Calendar.MILLISECOND, 0);
	this.add(Calendar.MINUTE, 1);
	break;
    case MyDailyRollingFileAppender.TOP_OF_HOUR:
	this.set(Calendar.MINUTE, 0);
	this.set(Calendar.SECOND, 0);
	this.set(Calendar.MILLISECOND, 0);
	this.add(Calendar.HOUR_OF_DAY, 1);
	break;
    case MyDailyRollingFileAppender.HALF_DAY:
	this.set(Calendar.MINUTE, 0);
	this.set(Calendar.SECOND, 0);
	this.set(Calendar.MILLISECOND, 0);
	int hour = get(Calendar.HOUR_OF_DAY);
	if(hour < 12) {
	  this.set(Calendar.HOUR_OF_DAY, 12);
	} else {
	  this.set(Calendar.HOUR_OF_DAY, 0);
	  this.add(Calendar.DAY_OF_MONTH, 1);
	}
	break;
    case MyDailyRollingFileAppender.TOP_OF_DAY:
	this.set(Calendar.HOUR_OF_DAY, 0);
	this.set(Calendar.MINUTE, 0);
	this.set(Calendar.SECOND, 0);
	this.set(Calendar.MILLISECOND, 0);
	this.add(Calendar.DATE, 1);
	break;
    case MyDailyRollingFileAppender.TOP_OF_WEEK:
	this.set(Calendar.DAY_OF_WEEK, getFirstDayOfWeek());
	this.set(Calendar.HOUR_OF_DAY, 0);
	this.set(Calendar.MINUTE, 0);
	this.set(Calendar.SECOND, 0);
	this.set(Calendar.MILLISECOND, 0);
	this.add(Calendar.WEEK_OF_YEAR, 1);
	break;
    case MyDailyRollingFileAppender.TOP_OF_MONTH:
	this.set(Calendar.DATE, 1);
	this.set(Calendar.HOUR_OF_DAY, 0);
	this.set(Calendar.MINUTE, 0);
	this.set(Calendar.SECOND, 0);
	this.set(Calendar.MILLISECOND, 0);
	this.add(Calendar.MONTH, 1);
	break;
    default:
	throw new IllegalStateException("Unknown periodicity type.");
    }
    return getTime();
  }
}
