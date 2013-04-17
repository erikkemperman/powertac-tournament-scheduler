package org.powertac.tourney.beans;

import javax.persistence.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static javax.persistence.GenerationType.IDENTITY;


@Entity
@Table(name = "levels")
public class Level
{
  private int levelId;
  private String levelName;
  private int competitionId;
  private int levelNr;
  private int nofRounds;
  private int nofWinners;
  private Date startTime;

  private Map<Integer, Round> roundMap = new HashMap<Integer, Round>();

  public Level ()
  {
  }

  @Transient
  public int getMaxBrokers ()
  {
    int total = 0;
    for (Round round : roundMap.values()) {
      total += round.getMaxBrokers();
    }
    return total;
  }

  @Transient
  public int getNofBrokers ()
  {
    int nofBrokers = 0;
    for (Round round : roundMap.values()) {
      nofBrokers += round.getBrokerMap().size();
    }
    return nofBrokers;
  }

  @OneToMany
  @JoinColumn(name = "levelId")
  @MapKey(name = "roundId")
  public Map<Integer, Round> getRoundMap ()
  {
    return roundMap;
  }
  public void setRoundMap (Map<Integer, Round> roundMap)
  {
    this.roundMap = roundMap;
  }

  //<editor-fold desc="Setters and Getters">
  @Id
  @GeneratedValue(strategy = IDENTITY)
  @Column(name = "levelId", unique = true, nullable = false)
  public int getLevelId ()
  {
    return levelId;
  }
  public void setLevelId (int levelId)
  {
    this.levelId = levelId;
  }

  @Column(name = "levelName")
  public String getLevelName ()
  {
    return levelName;
  }
  public void setLevelName (String levelName)
  {
    this.levelName = levelName;
  }

  @Column(name = "competitionId")
  public int getCompetitionId ()
  {
    return competitionId;
  }
  public void setCompetitionId (int competitionId)
  {
    this.competitionId = competitionId;
  }

  @Column(name = "levelNr")
  public int getLevelNr ()
  {
    return levelNr;
  }
  public void setLevelNr (int levelNr)
  {
    this.levelNr = levelNr;
  }

  @Column(name = "nofRounds")
  public int getNofRounds ()
  {
    return nofRounds;
  }
  public void setNofRounds (int nofRounds)
  {
    this.nofRounds = nofRounds;
  }

  @Column(name = "nofWinners")
  public int getNofWinners ()
  {
    return nofWinners;
  }
  public void setNofWinners (int nofWinners)
  {
    this.nofWinners = nofWinners;
  }

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "startTime", nullable = false, length = 10)
  public Date getStartTime ()
  {
    return startTime;
  }
  public void setStartTime (Date startTime)
  {
    this.startTime = startTime;
  }
  //</editor-fold>
}
