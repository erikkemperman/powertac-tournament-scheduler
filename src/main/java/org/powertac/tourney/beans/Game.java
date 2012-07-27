package org.powertac.tourney.beans;

import org.apache.log4j.Logger;
import org.powertac.tourney.services.Database;
import org.powertac.tourney.services.SpringApplicationContext;
import org.powertac.tourney.services.TournamentProperties;
import org.powertac.tourney.services.Utils;

import javax.persistence.*;
import java.io.File;
import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static javax.persistence.GenerationType.IDENTITY;


// Create hibernate mapping with annotations
@Entity
@Table(name = "games", catalog = "tourney", uniqueConstraints = {
		@UniqueConstraint(columnNames = "gameId")})
public class Game implements Serializable
{
  private static Logger log = Logger.getLogger("TMLogger");
  public static final String key = "game";

  private Date startTime;
  private Date readyTime;
  private int tourneyId = 0;
  private int gameId = 0;
  private int machineId;
  private String status = STATE.boot_pending.toString();
  private boolean hasBootstrap = false;
  private String brokers = "";
  private int maxBrokers = 1;

  private String gameName = "";
  private String jmsUrl = "";
  private String serverQueue = "";
  private String visualizerUrl = "";
  private String visualizerQueue = "";

  /*
  - Boot
    Game are initially set to boot_pending.
    When the jobs is sent to Jenkins, the TM sets it to in_progress.
    When done the Jenkins script sets it complete or failed when done,
    depending on the boot file. When the TM isn't able to send the job to
    Jenkins, the game is set to failed.

  - Game
    When the job is sent to Jenkins, the TM sets it to game_pending.
    When the sim is ready, the sim sets the game to game_ready.
    It also sets readyTime, to give the visualizer some time to log in before
    the brokers log in. Brokers are allowed to log in when game_ready and
    readyTime + X minutes.
    When all the brokers are logged in (or login timeout occurs), the sim sets
    it to in_progress.

    When the sim stops, the Jenkins script sets the game to complete.
    game_failed occurs when the script encounters problems downloading the POM-
    or boot-file, or when RunGame has problems sending the job to jenkins.
   */

  public static enum STATE {
    boot_pending, boot_in_progress, boot_complete, boot_failed,
    game_pending, game_ready, game_in_progress, game_complete, game_failed
  }

  public Game ()
  {
  }

  public Game (ResultSet rs)
  {
    try {
      setStatus(rs.getString("status"));
      setMaxBrokers(rs.getInt("maxBrokers"));
      setStartTime(Utils.dateFormatUTCmilli(rs.getString("startTime")));
      setReadyTime(rs.getString("readyTime"));
      setBrokers(rs.getString("brokers"));
      setTourneyId(rs.getInt("tourneyId"));
      setMachineId(rs.getInt("machineId"));
      setHasBootstrap(rs.getBoolean("hasBootstrap"));
      setGameName(rs.getString("gameName"));
      setGameId(rs.getInt("gameId"));
      setJmsUrl(rs.getString("jmsUrl"));
      setServerQueue(rs.getString("serverQueue"));
      setVisualizerUrl(rs.getString("visualizerUrl"));
      setVisualizerQueue(rs.getString("visualizerQueue"));
    }
    catch (Exception e) {
      log.error("Error creating game from result set");
      e.printStackTrace();
    }
  }

  @Transient
  public String getTournamentName ()
  {
    String result = "";

    Database db = new Database();
    try {
      db.startTrans();
      result = db.getTournamentByGameId(gameId).getTournamentName();
      db.commitTrans();
    }
    catch (Exception e) {
      db.abortTrans();
      e.printStackTrace();
    }

    return result;
  }

  @Transient
  public int getNumBrokersRegistered ()
  {
    Database db = new Database();
    int result = 0;

    try {
      db.startTrans();
      result = db.getBrokersInGame(gameId).size();
      db.commitTrans();
    }
    catch (SQLException e) {
      db.abortTrans();
      e.printStackTrace();
    }
    return result;
  }

  public void addBroker (int brokerId)
  {
    Database db = new Database();
    Broker b = new Broker("new");
    try {
      db.startTrans();
      b = db.getBroker(brokerId);
      db.addBrokerToGame(gameId, b);
      db.commitTrans();
    }
    catch (SQLException e) {
      db.abortTrans();
      e.printStackTrace();
    }
    brokers += b.getBrokerName() + ", ";
  }

  public Broker getBrokerRegistration (String authToken)
  {
    log.info("Broker token: " + authToken);
    Database db = new Database();
    Broker result = null;
    try {
      db.startTrans();
      List<Broker> allBrokers = db.getBrokersInGame(gameId);
      for (Broker b: allBrokers) {
        if (b.getBrokerAuthToken().equalsIgnoreCase(authToken)) {
          result = b;
          break;
        }
      }
      db.commitTrans();
    }
    catch (SQLException e) {
      db.abortTrans();
      e.printStackTrace();
    }
    return result;
  }

  @Transient
  public String getBrokersInGame ()
  {
    String result = "";
    List<Broker> brokersRegistered = new ArrayList<Broker>();

    Database db = new Database();
    try {
      db.startTrans();
      brokersRegistered = db.getBrokersInGame(gameId);
      db.commitTrans();
    }
    catch (Exception e) {
      db.abortTrans();
      e.printStackTrace();
    }

    for (Broker b: brokersRegistered) {
      result += b.getBrokerName() + ", ";
    }
    if (!result.isEmpty()) {
      result = result.substring(0, result.length()-2);
    }

    return result;
  }

  @Transient
  public String getBrokersInGameComplete ()
  {
    String result = "";
    List<Broker> brokersRegistered = new ArrayList<Broker>();

    Database db = new Database();
    try {
      db.startTrans();
      brokersRegistered = db.getBrokersInGameComplete(gameId);
      db.commitTrans();
    }
    catch (Exception e) {
      db.abortTrans();
      e.printStackTrace();
    }

    for (Broker b: brokersRegistered) {
      result += b.getBrokerName() + ", ";
    }
    if (!result.isEmpty()) {
      result = result.substring(0, result.length()-2);
    }

    return result;
  }

  // TODO Make this an object method, combine with Hibernate
  // TODO Add state machine for Game
  public static String handleStatus(String status, int gameId)
  {
    log.info(String.format("Received %s message from game: %s", status, gameId));

    STATE state;
    try {
      state = STATE.valueOf(status);
    }
    catch (Exception e) {
      return "error";
    }

    Scheduler scheduler = (Scheduler) SpringApplicationContext.getBean("scheduler");
    Database db = new Database();

    try {
      db.startTrans();
      Game g = db.getGame(gameId);
      if (g == null) {
        log.warn(String.format("Trying to set status %s on non-existing game : "
            + "%s", status, gameId));
        return "error";
      }
      Tournament t;

      db.updateGameStatusById(gameId, state);
      log.info(String.format("Update game: %s to %s", gameId, status));

      switch (state) {
        case boot_in_progress:
          // Remove bootfile, it shouldn't exist anyway
          g.removeBootFile();
          break;

        case boot_complete:
          db.updateGameBootstrapById(gameId, true);
          log.info(String.format("Update game: %s to hasBootstrap", gameId));

          db.updateGameFreeMachine(gameId);
          log.info("Freeing Machines for game: " + gameId);
          db.setMachineStatus(g.getMachineId(), Machine.STATE.idle);
          log.info("Setting machine " + g.getMachineId() + " to idle");
          Scheduler.bootRunning = false;
          break;

        case boot_failed:
          log.warn("BOOT " + gameId + " FAILED!");

          db.updateGameFreeMachine(gameId);
          log.info("Freeing Machines for game: " + gameId);
          db.setMachineStatus(g.getMachineId(), Machine.STATE.idle);
          log.info("Setting machine {0} to idle " +g.getMachineId());
          Scheduler.bootRunning = false;
          break;

        case game_ready:
          t = db.getTournamentByGameId(g.gameId);
          t.setTournametInProgress(db);
          g.setReadyTime(Utils.dateFormatUTCmilli(new Date()));
          db.setGameReadyTime(gameId);
          break;
        case game_in_progress:
          t = db.getTournamentByGameId(g.gameId);
          t.setTournametInProgress(db);
          break;

        case game_complete:
          db.updateGameFreeBrokers(gameId);
          log.info("Freeing Brokers for game: " + gameId);
          db.updateGameFreeMachine(gameId);
          log.info("Freeing Machines for game: " + gameId);
          scheduler.resetServer(g.getMachineId());
          db.setMachineStatus(g.getMachineId(), Machine.STATE.idle);
          log.info("Setting machine {0} to idle " + g.getMachineId());

					// If all games of tournament are complete, set tournament complete
					t = db.getTournamentByGameId(g.gameId);
          t.processGameFinished(db, g.getGameId());
          break;

        case game_failed:
          log.warn("GAME " + gameId + " FAILED!");

          db.updateGameFreeBrokers(gameId);
          log.info("Freeing Brokers for game: " + gameId);
          db.updateGameFreeMachine(gameId);
          log.info("Freeing Machines for game: " + gameId);
          scheduler.resetServer(g.getMachineId());
          db.setMachineStatus(g.getMachineId(), Machine.STATE.idle);
          log.info("Setting machine {0} to idle " + g.getMachineId());
          break;
      }

      db.commitTrans();
    }
    catch (Exception e) {
      db.abortTrans();
      e.printStackTrace();
    }
    return "success";
  }

  public void setState (STATE state)
  {
    Database db = new Database();
    try {
      db.startTrans();
      db.updateGameStatusById(gameId, state);
      db.commitTrans();
    }
    catch (Exception e) {
      db.abortTrans();
      e.printStackTrace();
    }
  }

  public void removeBootFile()
  {
    TournamentProperties properties = TournamentProperties.getProperties();
    String bootLocation = properties.getProperty("bootLocation") +
        gameId + "-boot.xml";
    File f = new File(bootLocation);

    if (!f.exists()) {
      return;
    }

    if (!f.canWrite()) {
      log.error("Write protected: " + bootLocation);
    }

    if (!f.delete()) {
      log.error("Failed to delete : " + bootLocation);
    }
  }

  public String toUTCStartTime ()
  {
    return Utils.dateFormatUTC(startTime);
  }
  public String toUTCReadyTime ()
  {
    return Utils.dateFormatUTC(readyTime);
  }

  public boolean stateEquals(STATE state)
  {
    return this.status.equals(state.toString());
  }

  public static List<Game> getGameList ()
  {
    List<Game> games = new ArrayList<Game>();

    Database db = new Database();
    try {
      db.startTrans();
      games = db.getGames();
      db.commitTrans();
    }
    catch (SQLException e) {
      db.abortTrans();
      e.printStackTrace();
    }

    return games;
  }

  public static List<Game> getGamesInTourney (String tourneyName)
  {
    List<Game> games = new ArrayList<Game>();

    Database db = new Database();
    try {
      db.startTrans();
      games = db.getGamesInTourney(tourneyName);
      db.commitTrans();
    }
    catch (SQLException e) {
      db.abortTrans();
      e.printStackTrace();
    }

    return games;
  }

  public static List<Game> getGameCompleteList ()
  {
    List<Game> games = new ArrayList<Game>();

    Database db = new Database();
    try {
      db.startTrans();
      games = db.getCompleteGames();
      db.commitTrans();
    }
    catch (SQLException e) {
      db.abortTrans();
      e.printStackTrace();
    }

    return games;
  }

  //<editor-fold desc="Setter and getters">
  @Temporal(TemporalType.DATE)
  @Column(name = "startTime", unique = false, nullable = false, length = 10)
  public Date getStartTime ()
  {
    return startTime;
  }

  public void setStartTime (Date startTime)
  {
    this.startTime = startTime;
  }

  public static String getKey ()
  {
    return key;
  }

  @Column(name = "status", unique = false, nullable = false)
  public String getStatus ()
  {
    return status;
  }

  public void setStatus (String status)
  {
    this.status = status;
  }

  @Column(name = "jmsUrl", unique = false, nullable = true)
  public String getJmsUrl ()
  {
    return jmsUrl;
  }

  public void setJmsUrl (String jmsUrl)
  {
    this.jmsUrl = jmsUrl;
  }
  
  @Column(name = "serverQueue", unique = false, nullable = true)
  public String getServerQueue ()
  {
    return serverQueue;
  }
  
  public void setServerQueue (String name)
  {
    this.serverQueue = name;
  }
  
  @Column(name="visualizerQueue", unique = false, nullable = true)
  public String getVisualizerQueue ()
  {
    return visualizerQueue;
  }
  
  public void setVisualizerQueue (String name)
  {
    this.visualizerQueue = name;
  }

  @Column(name = "maxBrokers", unique = false, nullable = true)
  public int getMaxBrokers ()
  {
    return maxBrokers;
  }

  public void setMaxBrokers (int maxBrokers)
  {
    this.maxBrokers = maxBrokers;
  }

  @Id
  @GeneratedValue(strategy = IDENTITY)
  @Column(name = "gameId", unique = true, nullable = false)
  public int getGameId ()
  {
    return gameId;
  }

  public void setGameId (int gameId)
  {
    this.gameId = gameId;
  }

  @Column(name = "hasBootstrap", unique = false, nullable = false)
  public boolean getHasBootstrap()
  {
    return hasBootstrap;
  }

  public void setHasBootstrap (boolean hasBootstrap)
  {
    this.hasBootstrap = hasBootstrap;
  }

  @Column(name = "gameName", unique = false, nullable = false)
  public String getGameName ()
  {
    return gameName;
  }

  public void setGameName (String gameName)
  {
    this.gameName = gameName;
  }

  @Column(name = "visualizerUrl", unique = false, nullable = false)
  public String getVisualizerUrl ()
  {
    return visualizerUrl;
  }

  public void setVisualizerUrl (String visualizerUrl)
  {
    this.visualizerUrl = visualizerUrl;
  }

  @Temporal(TemporalType.DATE)
  @Column(name = "readyTime", unique = false, nullable = false)
  public Date getReadyTime ()
  {
    return readyTime;
  }
  public void setReadyTime (String readyTime)
  {
    try {
      this.readyTime = Utils.dateFormatUTCmilli(readyTime);
    }
    catch (Exception ignored) {
      this.readyTime = null;
    }
  }

  @Column(name = "tourneyId", unique = false, nullable = false)
  public int getTourneyId ()
  {
    return tourneyId;
  }

  public void setTourneyId (int tourneyId)
  {
    this.tourneyId = tourneyId;
  }

  // Comma delimited list of brokers to be sent to the server command line
  @Column(name = "brokers", unique = false, nullable = false)
  public String getBrokers ()
  {
    return brokers;
  }

  public void setBrokers (String brokers)
  {
    this.brokers = brokers;
  }

  @Column(name = "machineId", unique = false, nullable = true)
  public int getMachineId ()
  {
    return machineId;
  }

  public void setMachineId (int machineId)
  {
    this.machineId = machineId;
  }
  //</editor-fold>
}
