package org.powertac.tournament.schedulers;

import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.powertac.tournament.beans.Agent;
import org.powertac.tournament.beans.Broker;
import org.powertac.tournament.beans.Game;
import org.powertac.tournament.beans.Level;
import org.powertac.tournament.beans.Round;
import org.powertac.tournament.beans.Tournament;
import org.powertac.tournament.services.HibernateUtil;
import org.powertac.tournament.services.MemStore;
import org.powertac.tournament.services.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/*
 * Create games (and agents) for all gameTypes in a round
 */
public class RoundScheduler
{
  private static Logger log = Utils.getLogger();

  private Session session;

  private Round round;
  private List<Broker> brokers;
  private List<Game> games;
  private int[] gameTypes;
  private int[] multipliers;
  private int gameCounter;

  public RoundScheduler (Round round)
  {
    this.round = round;
  }

  public boolean createGamesForLoadedRound ()
  {
    if (round.getSize() > 0) {
      log.info("Round already scheduled : " + round.getRoundName());
      return false;
    }
    else if (!round.isStarted()) {
      log.info("Round not ready : " + round.getRoundName());
      return false;
    }
    log.info("Round available : " + round.getRoundName());

    brokers = new ArrayList<>(round.getBrokerMap().values());
    gameTypes = new int[]{round.getSize1(), round.getSize2(), round.getSize3()};
    multipliers = new int[]{
        round.getMultiplier1(), round.getMultiplier2(), round.getMultiplier3()};
    setCounter();

    session = HibernateUtil.getSession();
    Transaction transaction = session.beginTransaction();
    try {
      if (brokers.size() == 0) {
        log.info("Round " + round.getRoundName()
            + " has no brokers registered, setting to complete");
        round.setStateToComplete();
        session.update(round);
        transaction.commit();
        return true;
      }

      doTheKailash();
      round.setStateToInProgress();
      session.update(round);

      transaction.commit();
    }
    catch (Exception e) {
      transaction.rollback();
      e.printStackTrace();
    }
    finally {
      session.close();
    }

    return true;
  }

  private void setCounter ()
  {
    // Counter the total numbers in tournament
    gameCounter = 1;
    // TODO Check if needed i.c.m. Forecaster
    try {
      // Count the games in previous levels (if any)
      Tournament tournament = round.getLevel().getTournament();
      int index = round.getLevel().getLevelNr();
      while (index-- > 0) {
        Level prevLevel = tournament.getLevelMap().get(index);
        for (Round prevRound : prevLevel.getRoundMap().values()) {
          gameCounter += prevRound.getGameMap().size();
        }
      }

      // Count games in previous (sibling) rounds
      String[] parts = round.getRoundName().split("_");
      int roundNr = Integer.parseInt(parts[parts.length - 1]);
      gameCounter += roundNr * getNofGamesPerRound();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void doTheKailash ()
  {
    log.info(String.format("Doing the Kailash, types = %s , multipliers = %s",
        Arrays.toString(gameTypes), Arrays.toString(multipliers)));
    String brokersString = "";
    for (Broker b : brokers) {
      brokersString += b.getBrokerId() + " ";
    }
    log.info("Broker ids : " + brokersString);

    // Get a list of all the games for all types and multipliers
    games = new ArrayList<>();
    for (int i = 0; i < (gameTypes.length); i++) {
      for (int j = 0; j < multipliers[i]; j++) {
        createGamesAgents(brokers, gameTypes[i], games);
      }
    }

    saveGamesAgents();
  }

  private List<String> getGameStrings (List<Broker> brokers, int gameType)
  {
    List<String> gameStrings = new ArrayList<>();

    for (int i = 0; i < (int) Math.pow(2, brokers.size()); i++) {
      // Write as binary + pad with leading zeros
      String gameString = Integer.toBinaryString(i);
      while (gameString.length() < brokers.size()) {
        gameString = '0' + gameString;
      }

      // Count number of 1's, representing participating players
      int count = 0;
      for (int j = 0; j < gameString.length(); j++) {
        if (gameString.charAt(j) == '1') {
          count++;
        }
      }

      // We need an equal amount of participants as the gameType
      if (count == gameType) {
        gameStrings.add(gameString);
      }
    }

    return gameStrings;
  }

  // Create a set of games for a set of brokers for a specific gameSize
  public void createGamesAgents (List<Broker> brokers, int gameType,
                                 List<Game> games)
  {
    // No use scheduling gamesTypes > # brokers
    gameType = Math.min(gameType, brokers.size());

    // Get binary string representations of games
    List<String> gameStrings = getGameStrings(brokers, gameType);

    // Create game and agents for every gameString

    // TODO Start backwards ??

    for (String gameString : gameStrings) {
      // Create game name
      String gameName = Game.createGameName(
          round.getRoundName(), gameType, gameCounter++);

      // Create game
      Game game = Game.createGame(round, gameName);
      games.add(game);

      // Add agents to the game
      for (int i = 0; i < gameString.length(); i++) {
        if (gameString.charAt(i) == '1') {
          Agent agent = Agent.createAgent(brokers.get(i), game);
          game.getAgentMap().put(brokers.get(i).getBrokerId(), agent);
        }
      }
    }
  }

  private void saveGamesAgents ()
  {
    // Only use stored lengths if they are applicable / not missing
    List<Integer> lengths = MemStore.getForecastLengths(round.getRoundId());
    if (lengths == null || games.size() != lengths.size()) {
      lengths = null;
      log.info("Not using stored game lengths");
    }
    else {
      log.info("Using stored game lengths");
    }

    int count = 0;
    for (Game game : games) {
      if (lengths != null) {
        game.setGameLength(lengths.get(count++));
      }
      else {
        game.setGameLength(game.computeGameLength());
      }

      session.save(game);
      log.info(String.format("Created game %s", game.getGameId()));

      for (Agent agent : game.getAgentMap().values()) {
        session.save(agent);
        log.info(
            String.format("Added broker: %s", agent.getBroker().getBrokerId()));
      }
    }
  }

  private int getNofGamesPerRound ()
  {
    int count = 0;
    for (int i = 0; i < (gameTypes.length); i++) {
      for (int j = 0; j < multipliers[i]; j++) {
        int gameType = Math.min(gameTypes[i], brokers.size());
        List<String> gameStrings = getGameStrings(brokers, gameType);
        count += gameStrings.size();
      }
    }
    return count;
  }
}
