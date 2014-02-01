package jnode.install;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.j256.ormlite.table.TableUtils;

import jnode.dao.GenericDAO;
import jnode.dto.Echoarea;
import jnode.dto.Echomail;
import jnode.dto.Link;
import jnode.dto.LinkOption;
import jnode.dto.Rewrite;
import jnode.dto.Route;
import jnode.dto.Subscription;
import jnode.dto.Rewrite.Type;
import jnode.dto.Robot;
import jnode.dto.Version;
import jnode.ftn.FtnTools;
import jnode.ftn.types.FtnAddress;
import jnode.install.support.Dupe_1_4;
import jnode.install.support.LinkOption_1_1;
import jnode.install.support.Rewrite_1_4;
import jnode.install.support.Route_1_2;
import jnode.logger.Logger;
import jnode.main.MainHandler;
import jnode.main.SystemInfo;
import jnode.orm.ORMManager;
import jnode.robot.AreaFix;
import jnode.robot.FileFix;
import jnode.robot.ScriptFix;

public class InstallUtil {
	private static final Logger logger = Logger.getLogger(InstallUtil.class);
	private GenericDAO<Version> versionDao;

	public InstallUtil() {
		versionDao = ORMManager.get(Version.class);
		List<Version> versions = versionDao
				.getOrderLimitAnd(1, "int_at", false);
		if (versions.size() == 0) {
			doInstall();
		} else {
			Version ver = versions.get(0);
			logger.l1("You have installed " + ver.toString());
			checkForLastVersion(ver);
		}
	}

	private void doInstall() {
		logger.l1("[+] Creating database data");
		SystemInfo info = MainHandler.getCurrentInstance().getInfo();

		// robots
		Robot areafix = new Robot();
		areafix.setClassName(AreaFix.class.getCanonicalName());
		areafix.setRobot("areafix");
		ORMManager.get(Robot.class).save(areafix);
		Robot filefix = new Robot();
		filefix.setClassName(FileFix.class.getCanonicalName());
		filefix.setRobot("filefix");
		ORMManager.get(Robot.class).save(filefix);

		Robot scriptfix = new Robot();
		scriptfix.setClassName(ScriptFix.class.getCanonicalName());
		scriptfix.setRobot("scriptfix");
		ORMManager.get(Robot.class).save(scriptfix);

		logger.l1("[+] Robots created");

		// owner's point
		String ownAddr = FtnTools.getPrimaryFtnAddress().toString() + ".1";
		Link owner = ORMManager.get(Link.class).getFirstAnd("ftn_address", "=",
				ownAddr);
		if (owner == null) {
			owner = new Link();
			owner.setLinkName(info.getSysop());
			owner.setLinkAddress(ownAddr);
			owner.setProtocolHost("-");
			owner.setProtocolPort(0);
			owner.setPaketPassword(FtnTools.generate8d());
			owner.setProtocolPassword(owner.getPaketPassword());
			ORMManager.get(Link.class).save(owner);
			logger.l1("[+] owner point account created");
			logger.l1(String.format("\n\tFTN: %s\n\tAka: %s\n\tPassword: %s\n",
					owner.getLinkAddress(), owner.getLinkName(),
					owner.getPaketPassword()));
			long nice = 1;
			Rewrite rw = new Rewrite();
			rw.setType(Type.NETMAIL);
			rw.setLast(true);
			rw.setNice(nice++);
			rw.setOrig_from_addr("^"
					+ ownAddr.replace(".", "\\.").replace("/", "\\/") + "$");
			rw.setNew_from_addr(FtnTools.getPrimaryFtnAddress().toString());
			ORMManager.get(Rewrite.class).save(rw);

			for (FtnAddress address : info.getAddressList()) {
				Rewrite rw2 = new Rewrite();
				rw2.setType(Type.NETMAIL);
				rw2.setLast(true);
				rw2.setNice(nice++);
				rw2.setOrig_to_addr("^"
						+ address.toString().replace(".", "\\.")
								.replace("/", "\\/") + "$");
				rw2.setNew_to_addr(ownAddr);
				ORMManager.get(Rewrite.class).save(rw2);
			}
			logger.l1("[+] Basic rewrites for owner's point created!");
		}

		{
			boolean route = !ORMManager.get(Route.class).getAll().isEmpty();
			if (!route) {
				List<Link> links = ORMManager.get(Link.class).getAll();
				for (Link l : links) {
					FtnAddress a = new FtnAddress(l.getLinkAddress());
					if (a.getPoint() == 0) { // server side link
						Route defroute = new Route();
						defroute.setNice(Long.MAX_VALUE);
						defroute.setRouteVia(l);
						ORMManager.get(Route.class).save(defroute);
						logger.l1("[+] all messages will be routed through "
								+ l.getLinkAddress());
						route = true;
						break;
					}
				}
				if (!route) {
					logger.l1("[-] link for default route now found. Add it manually");
				}
			}
		}

		{
			List<Echoarea> areas = ORMManager.get(Echoarea.class).getAll();
			if (areas.isEmpty()) {
				Echoarea local = new Echoarea();
				local.setDescription("Local echoarea");
				local.setName(FtnTools.getPrimaryFtnAddress().getNode()
						+ ".local");
				local.setGroup("");
				ORMManager.get(Echoarea.class).save(local);
				Subscription sub = new Subscription();
				sub.setArea(local);
				sub.setLink(owner);
				ORMManager.get(Subscription.class).save(sub);
				FtnTools.writeEchomail(local, "Your new jNode installation",
						"Welcome aboard!\n\nYou've just installed. Enjoy in Fidonet now!");
				logger.l1("[+] created local echoarea " + local.getName());
			}
		}
		ORMManager.get(Version.class).save(DefaultVersion.getSelf());
		logger.l1("Installation completed!");
	}

	private void checkForLastVersion(Version ver) {
		updateFromVersion(ver);
	}

	private void execQuery(String query) {
		ORMManager.get(Version.class).executeRaw(query);
	}

	private void updateFromVersion(Version ver) {
		if (ver.equals("1.0")) {
			execQuery("ALTER TABLE netmail ADD last_modified BIGINT NOT NULL DEFAULT 0;");
			ver.setMinorVersion(1L);
			ORMManager.get(Version.class).save(ver);
			logger.l1(String.format("Upgraded to %s", ver.toString()));
		}
		if (ver.equals("1.1")) {
			try {
				List<LinkOption_1_1> options2 = ORMManager.get(
						LinkOption_1_1.class).getAll();

				ArrayList<LinkOption> options = new ArrayList<>();
				for (LinkOption_1_1 l2 : options2) {
					LinkOption l = new LinkOption();
					l.setLink(l2.getLink());
					l.setOption(l2.getOption());
					l.setValue(l2.getValue());
					options.add(l);
				}
				TableUtils.dropTable(ORMManager.getSource(),
						LinkOption_1_1.class, true);
				TableUtils
						.createTable(ORMManager.getSource(), LinkOption.class);
				for (LinkOption l : options) {
					ORMManager.get(LinkOption.class).save(l);
				}
				ver.setMinorVersion(2L);
				ORMManager.get(Version.class).save(ver);
				logger.l1(String.format("Upgraded to %s", ver.toString()));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (ver.equals("1.2")) {
			try {
				List<Route_1_2> routes = ORMManager.get(Route_1_2.class)
						.getAll();
				LinkedList<Route> newroute = new LinkedList<>();
				for (Route_1_2 r2 : routes) {
					Route r = new Route();
					r.setNice(r2.getNice());
					r.setFromAddr(r2.getFromAddr());
					r.setFromName(r2.getFromName());
					r.setToAddr(r2.getToAddr());
					r.setToName(r2.getToName());
					r.setSubject(r2.getSubject());
					r.setRouteVia(r2.getRouteVia());
					newroute.add(r);
				}
				TableUtils.dropTable(ORMManager.getSource(), Route_1_2.class,
						true);
				TableUtils.createTable(ORMManager.getSource(), Route.class);
				for (Route r : newroute) {
					ORMManager.get(Route.class).save(r);
				}
				ver.setMinorVersion(3L);
				ORMManager.get(Version.class).save(ver);
				logger.l1(String.format("Upgraded to %s", ver.toString()));
			} catch (Exception e) {
				logger.l1("Exception while updating to 1.3", e);
			}
		}
		if (ver.equals("1.3")) {
			execQuery("ALTER TABLE links ADD COLUMN address varchar(255) NOT NULL DEFAULT '-';");
			execQuery("UPDATE links SET address=host;");
			execQuery("ALTER TABLE links DROP COLUMN host;");
			execQuery("ALTER TABLE links DROP COLUMN port;");
			ver.setMinorVersion(4L);
			ORMManager.get(Version.class).save(ver);
			logger.l1(String.format("Upgraded to %s", ver.toString()));
		}
		if (ver.equals("1.4")) {
			try {
				execQuery("ALTER TABLE echomail ADD COLUMN msgid VARCHAR(255);");
				execQuery("CREATE INDEX echomail_msgid ON echomail ( msgid );");
				long lastid = 0;
				List<Echomail> mail;
				Pattern pattern = Pattern.compile(".*^\001MSGID: (.*)$.*",
						Pattern.MULTILINE);
				do {
					int cnt = 0;
					mail = ORMManager.get(Echomail.class).getOrderLimitAnd(
							1000, "id", true, "id", ">", lastid);
					for (Echomail m : mail) {
						lastid = m.getId();
						Matcher ma = pattern.matcher(m.getText());
						if (ma.find()) {
							String msgid = ma.group(1);
							String text = m.getText().replace(
									ma.group() + "\n", "");
							ORMManager.get(Echomail.class).update("msgid",
									msgid, "id", "=", m.getId());
							ORMManager.get(Echomail.class).update("text", text,
									"id", "=", m.getId());
							cnt++;
						}
					}
					logger.l2("Found " + cnt + " msgids");
				} while (!mail.isEmpty());
				TableUtils.dropTable(ORMManager.getSource(), Dupe_1_4.class,
						true);
				List<Rewrite_1_4> rewrites = ORMManager.get(Rewrite_1_4.class)
						.getAll();
				LinkedList<Rewrite> newRewrites = new LinkedList<>();
				for (Rewrite_1_4 o : rewrites) {
					Rewrite n = new Rewrite();
					n.setNice(o.getNice());
					n.setType(o.getType());
					n.setLast(o.isLast());
					n.setOrig_from_addr(o.getOrig_from_addr());
					n.setOrig_from_name(o.getOrig_from_name());
					n.setOrig_to_addr(o.getOrig_to_addr());
					n.setOrig_to_name(o.getOrig_to_name());
					n.setOrig_subject(o.getOrig_subject());

					n.setNew_from_addr(o.getNew_from_addr());
					n.setNew_from_name(o.getNew_from_name());
					n.setNew_to_addr(o.getNew_to_addr());
					n.setNew_to_name(o.getNew_to_name());
					n.setNew_subject(o.getNew_subject());
					newRewrites.add(n);
				}
				TableUtils.dropTable(ORMManager.getSource(), Rewrite_1_4.class,
						true);
				TableUtils.createTable(ORMManager.getSource(), Rewrite.class);
				for (Rewrite r : newRewrites) {
					ORMManager.get(Rewrite.class).save(r);
				}
				ver.setMinorVersion(5L);
				ORMManager.get(Version.class).save(ver);
				logger.l1(String.format("Upgraded to %s", ver.toString()));
			} catch (Exception e) {
				logger.l1("Exception while updating to 1.5", e);
			}
		}
	}
}
