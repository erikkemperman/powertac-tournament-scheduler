package org.powertac.tourney.actions;

import org.apache.log4j.Logger;
import org.powertac.tourney.beans.*;
import org.powertac.tourney.services.*;

import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.RequestScoped;
import javax.faces.context.FacesContext;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;
import java.util.List;

@ManagedBean
@RequestScoped
public class ActionOverview
{
  private static Logger log = Logger.getLogger("TMLogger");

  private String sortColumnBrokers = null;
  private boolean sortAscendingBrokers = true;

  private String sortColumnTournaments = null;
  private boolean sortAscendingTournaments = true;

  private String sortColumnGames = null;
  private boolean sortAscendingGames = true;

  public ActionOverview()
  {
  }

  public List<Broker> getBrokerList ()
  {
    return Broker.getBrokerList();
  }

  public List<Tournament> getTournamentList ()
  {
    return Tournament.getTournamentList();
  }

  public List<Game> getGameList ()
  {
    return Game.getGameList();
  }

  // TODO Should be a Game method
  public void restartGame (Game g)
  {
    Database db = new Database();
    int gameId = g.getGameId();
    log.info("Restarting Game " + gameId + " has status: " + g.getStatus());
    Tournament t = new Tournament();

    try {
      db.startTrans();
      t = db.getTournamentByGameId(gameId);

      db.setMachineStatus(g.getMachineId(), Machine.STATE.idle);
      log.info("Setting machine: " + g.getMachineId() + " to idle");
      db.commitTrans();
    }
    catch (SQLException e) {
      db.abortTrans();
      e.printStackTrace();
    }

    if (g.stateEquals(Game.STATE.boot_failed) ||
        g.stateEquals(Game.STATE.boot_pending) ||
        g.stateEquals(Game.STATE.boot_in_progress) ) {
      log.info("Attempting to restart bootstrap " + gameId);

      RunBootstrap runBootstrap = new RunBootstrap(gameId, t.getPomId());
      new Thread(runBootstrap).start();
    }
    else if (g.stateEquals(Game.STATE.game_failed) ||
        g.stateEquals(Game.STATE.game_in_progress) ||
        g.stateEquals(Game.STATE.boot_failed) ) {
      log.info("Attempting to restart sim " + gameId);

      RunGame runGame = new RunGame(g.getGameId(), t.getPomId());
      new Thread(runGame).start();
    }
  }

  // TODO Should be a Game method??
  public void stopGame (Game g)
  {
    log.info("Trying to stop game: " + g.getGameId());

    Database db = new Database();
    try {
      db.startTrans();

      // Get the machineName and stop the job on Jenkins
      TournamentProperties properties = TournamentProperties.getProperties();
      String machineName = db.getMachineById(g.getMachineId()).getName();
      String stopUrl = properties.getProperty("jenkinsLocation")
          + "computer/" + machineName + "/executors/0/stop";
      log.info("Stop url: " + stopUrl);

      try {
        URL url = new URL(stopUrl);
        URLConnection conn = url.openConnection();
        conn.getInputStream();
      }
      catch (Exception ignored) {}
      log.info("Stopped job on Jenkins");

      // Reset game and machine on TM
      if (g.getStatus().equals(Game.STATE.boot_in_progress.toString())) {
        log.info("Resetting boot game: " + g.getGameId()
            + " on machine: " + machineName);
        db.updateGameBootstrapById(g.getGameId(), false);
        g.removeBootFile();
        db.updateGameStatusById(g.getGameId(), Game.STATE.boot_pending);
        Scheduler.bootRunning = false;
      }
      else if ((g.getStatus().equals(Game.STATE.game_pending.toString())) ||
          (g.getStatus().equals(Game.STATE.game_ready.toString())) ||
          (g.getStatus().equals(Game.STATE.game_in_progress.toString())) ) {
        log.info("Resetting sim game: " + g.getGameId()
            + " on machine: " + machineName);

        db.updateGameStatusById(g.getGameId(), Game.STATE.boot_complete);
        db.clearGameReadyTime(g.getGameId());

        Scheduler scheduler =
            (Scheduler) SpringApplicationContext.getBean("scheduler");
        scheduler.resetServer(g.getMachineId());
      }

      db.updateGameFreeMachine(g.getGameId());
      db.setMachineStatus(g.getMachineId(), Machine.STATE.idle);

      db.commitTrans();
    }
    catch (Exception e) {
      e.printStackTrace();
      db.abortTrans();

      log.error("Failed to completely stop game: " + g.getGameId());
      String msg = "Error stopping game : " + g.getGameId();
      FacesMessage fm = new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null);
      FacesContext.getCurrentInstance().addMessage("gamesForm", fm);
    }
  }

  public void refresh ()
  {
  }

  //<editor-fold desc="Setters and Getters">
  public boolean isSortAscendingBrokers()
  {
    return sortAscendingBrokers;
  }
  public void setSortAscendingBrokers(boolean sortAscendingBrokers)
  {
    this.sortAscendingBrokers = sortAscendingBrokers;
  }

  public String getSortColumnBrokers()
  {
    return sortColumnBrokers;
  }
  public void setSortColumnBrokers(String sortColumnBrokers)
  {
    this.sortColumnBrokers = sortColumnBrokers;
  }

  public String getSortColumnTournaments ()
  {
    return sortColumnTournaments;
  }
  public void setSortColumnTournaments (String sortColumnTournaments)
  {
    this.sortColumnTournaments = sortColumnTournaments;
  }

  public boolean isSortAscendingTournaments ()
  {
    return sortAscendingTournaments;
  }
  public void setSortAscendingTournaments (boolean sortAscendingTournaments)
  {
    this.sortAscendingTournaments = sortAscendingTournaments;
  }

  public String getSortColumnGames ()
  {
    return sortColumnGames;
  }
  public void setSortColumnGames (String sortColumnGames)
  {
    this.sortColumnGames = sortColumnGames;
  }

  public boolean isSortAscendingGames ()
  {
    return sortAscendingGames;
  }
  public void setSortAscendingGames (boolean sortAscendingGames)
  {
    this.sortAscendingGames = sortAscendingGames;
  }
  //</editor-fold>
}