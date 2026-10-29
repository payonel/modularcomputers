package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.device.CardKind;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.device.InternetCard;
import com.payonel.modularcomputers.device.NetworkCard;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.internet.InternetPolicy;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.machine.CpuTier;
import com.payonel.modularcomputers.machine.Eeprom;
import com.payonel.modularcomputers.machine.Hardware;
import com.payonel.modularcomputers.machine.LuaScripts;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.machine.TestBus;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * The manual's programs for the network cards, WLAN cards and the internet card
 * (docs/examples/network-*, wlan-*, internet-*), each run as a player would run it: on a computer
 * with the card, against another computer running mcos, a bare card on another computer's bus, or a
 * web server of the test's own. They check what the programs do to devices and files.
 */
class NetworkExamplesTest extends McosHarness {
	/** One world for this test's cards (any key: a level's). */
	private final Object air = new Object();

	/** Another computer running mcos from a disk of its own, on this test's server thread, with a card of {@code kind} at {@code pos}. */
	private final class Other {
		final Terminal screen = new Terminal(Resolution.DEFAULT);
		final Machine machine = new Machine(new Motherboard(Hardware.computer(), new Bus()));
		final Path root;
		final NetworkCard card;

		Other(String name, CardKind kind, BlockPos pos, UUID id) {
			root = world.resolve(name);
			LuaScripts.copyTree("mcos", root);
			card = card(kind, pos, id);
			Eeprom eeprom = new Eeprom(LuaScripts.read("eeprom/boot"));
			Map<String, Device> devices = new LinkedHashMap<>();
			devices.put("id:screen", screen);
			devices.put("sata:0", new Disk(DiskTier.TIER_1, root));
			devices.put("eeprom", eeprom);
			devices.put(Motherboard.exp(0), card);
			machine.board().setCpu(new Cpu(CpuTier.TIER_3));
			machine.setServer(server::add);
			TestBus.of(machine, devices);
			machine.board().setEeprom(eeprom);
			assertThat(machine.pressPower()).isEqualTo(Machine.PowerPress.ON);
			ticks.add(() -> {
				TestServer.awaitHost(machine); // (the server ticks at every computer's pace, this one's too)
				machine.tick();
			});
		}

		void file(String path, String text) {
			try {
				Path at = root.resolve(path);
				Files.createDirectories(at.getParent());
				Files.writeString(at, text);
			} catch (IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}

		void type(String keys) {
			for (int i = 0; i < keys.length(); i++) {
				screen.press(keys.substring(i, i + 1));
			}
		}

		/** Until it has read every key and every process waits. */
		void untilIdle() {
			settle(() -> !screen.hasKeys() && machine.runner().map(r -> r.state() == Runner.State.WAITING).orElse(false));
		}
	}

	/** A card of {@code kind} with this id, at {@code pos} in this test's world (two wired cards in one block share a wire). */
	private NetworkCard card(CardKind kind, BlockPos pos, UUID id) {
		var card = new NetworkCard(kind);
		card.idFrom(() -> id);
		card.place(air, pos, 0);
		return card;
	}

	/** A card on another computer's bus that runs a program: it takes frames, and the test reads them. */
	private NetworkCard peer(CardKind kind, BlockPos pos) {
		var peer = card(kind, pos, UUID.randomUUID());
		peer.power().supply(this);
		peer.accepting(true);
		return peer;
	}

	/** This computer's own card, in its first expansion slot. */
	private NetworkCard mine(CardKind kind, BlockPos pos) {
		var card = card(kind, pos, UUID.randomUUID());
		devices.put(Motherboard.exp(0), card);
		return card;
	}

	private static Arguments args(Object... values) {
		return new Arguments(Arrays.asList(values));
	}

	/** Every frame waiting in a card, as it took them. */
	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> frames(NetworkCard card) {
		List<Map<String, Object>> frames = new java.util.ArrayList<>();
		Object frame;
		while ((frame = card.receive(args()).first()) != null) {
			frames.add((Map<String, Object>) frame);
		}
		return frames;
	}

	// Network cards.

	@Test
	void theTrafficLoggerWritesALineForEachFrameInAndOut() {
		NetworkCard card = mine(CardKind.NETWORK, BlockPos.ZERO);
		NetworkCard peer = peer(CardKind.NETWORK, BlockPos.ZERO);
		boot();
		file("traffic.lua", example("network-traffic"));
		type("lua /traffic.lua /log &\r");
		untilIdle();
		server.add(() -> peer.send(args(card.address(), "hello")));
		settle(() -> Files.isRegularFile(root().resolve("log")) && read(root().resolve("log")).contains(" in "));
		type("echo hi | nc -u " + peer.address() + " 9\r");
		settle(() -> read(root().resolve("log")).contains(" out "));
		List<String> lines = read(root().resolve("log")).lines().map(String::strip).toList();
		assertThat(lines.get(0)).as("a time, then the card's line").matches("[0-9.]+ in " + peer.address() + " 0\\.0m 5 taken");
		assertThat(lines.get(1)).matches("[0-9.]+ out " + peer.address() + " 0\\.0m 8 taken");
	}

	@Test
	void theChatBroadcastsWhatsTypedAndTheOtherComputerShowsIt() {
		UUID theirs = UUID.randomUUID();
		mine(CardKind.NETWORK, BlockPos.ZERO);
		file("chat.lua", example("network-chat"));
		boot();
		Other other = new Other("other", CardKind.NETWORK, BlockPos.ZERO, theirs);
		other.file("chat.lua", example("network-chat"));
		other.untilIdle();
		other.type("lua /chat.lua bob > /heard\r");
		other.untilIdle(); // (its socket bound, waiting)
		type("echo hello | lua /chat.lua alice 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("err"))).isEmpty();
		other.type("\u0004"); // Ctrl+D: its input ends, and its output is closed
		untilFile(other.root.resolve("heard"), "alice: hello\n");
	}

	@Test
	void theEchoServerSendsBackWhatEachClientSends() {
		UUID theirs = UUID.randomUUID();
		NetworkCard card = mine(CardKind.NETWORK, BlockPos.ZERO);
		file("echo.lua", example("network-echo"));
		boot();
		type("lua /echo.lua &\r");
		untilIdle();
		Other other = new Other("other", CardKind.NETWORK, BlockPos.ZERO, theirs);
		other.untilIdle();
		other.type("echo hello | nc " + card.address() + " 7 > /one; echo again | nc " + card.address() + " 7 > /two; touch /done\r");
		untilFile(other.root.resolve("done"), "");
		assertThat(read(other.root.resolve("one"))).isEqualTo("hello\n");
		assertThat(read(other.root.resolve("two"))).as("and the next client").isEqualTo("again\n");
	}

	@Test
	void theWakeOnLanProgramSetsTheMessageAndTurnsAnotherComputerOn() {
		UUID theirs = UUID.randomUUID();
		mine(CardKind.NETWORK, BlockPos.ZERO);
		file("wol.lua", example("network-wol"));
		boot();
		Other other = new Other("other", CardKind.NETWORK, BlockPos.ZERO, theirs);
		other.file("wol.lua", example("network-wol"));
		other.untilIdle();
		other.type("lua /wol.lua enable letmein > /was; poweroff\r");
		settle(() -> !other.machine.isPowered());
		assertThat(read(other.root.resolve("was"))).isEqualTo("was: (none)\n");
		assertThat(other.card.getWake(args()).first()).isEqualTo("letmein");
		type("lua /wol.lua " + theirs + " nope; echo $? > /st1; lua /wol.lua " + theirs + " letmein; echo $? > /st2\r");
		untilFile(root().resolve("st2"), "0\n");
		settle(other.machine::isPowered);
		assertThat(read(root().resolve("st1"))).as("taken by its listening card, though not its message").isEqualTo("0\n");
	}

	// WLAN cards.

	@Test
	void theTunerTurnsTheSignalDownToTheWeakestThatReachesTheOtherCard() {
		NetworkCard card = mine(CardKind.TIER2_WLAN, BlockPos.ZERO);
		NetworkCard peer = peer(CardKind.TIER1_WLAN, new BlockPos(0, 0, 7));
		boot();
		file("tune.lua", example("wlan-tune"));
		type("lua /tune.lua " + peer.address() + " > /out 2> /err; echo $? > /st\r");
		untilFile(root().resolve("st"), "0\n");
		assertThat(read(root().resolve("err"))).isEmpty();
		assertThat(card.range()).as("7 blocks away").isEqualTo(7);
		assertThat(frames(peer)).as("its probes reached it").isNotEmpty().allSatisfy(frame -> assertThat(frame).containsEntry("data", "tune"));
	}

	@Test
	void theBeaconSetsItsStrengthAndBroadcastsTheComputersName() {
		NetworkCard card = mine(CardKind.TIER2_WLAN, BlockPos.ZERO);
		NetworkCard near = peer(CardKind.TIER1_WLAN, new BlockPos(5, 0, 0));
		NetworkCard far = peer(CardKind.TIER1_WLAN, new BlockPos(40, 0, 0));
		boot();
		file("beacon.lua", example("wlan-beacon"));
		type("lua /beacon.lua 20 2> /dev/null &\r");
		settle(near::interruptPending); // (a beacon came)
		assertThat(card.range()).isEqualTo(20);
		assertThat(frames(far)).as("40 blocks away: out of reach").isEmpty();
		Map<String, Object> beacon = frames(near).get(0);
		String data = (String) beacon.get("data");
		assertThat(beacon).containsEntry("broadcast", true).containsEntry("from", card.address());
		assertThat(data.charAt(0)).as("UDP").isEqualTo((char) 17);
		assertThat(((data.charAt(3) << 8) | data.charAt(4))).as("to the beacon's port").isEqualTo(4001);
		assertThat(data.substring(5)).as("the computer's name: its card's address, none set").isEqualTo(card.address() + "\n");
	}

	@Test
	void theSurveyWritesEveryCardHeardNearestFirst() {
		mine(CardKind.TIER1_WLAN, BlockPos.ZERO);
		NetworkCard near = peer(CardKind.TIER2_WLAN, new BlockPos(3, 0, 0));
		NetworkCard far = peer(CardKind.TIER2_WLAN, new BlockPos(0, 0, 30));
		boot();
		file("survey.lua", example("wlan-survey"));
		type("lua /survey.lua /survey 5 > /dev/null &\r");
		untilIdle();
		server.add(() -> far.broadcast(args("x")));
		server.add(() -> near.broadcast(args("y")));
		server.add(() -> far.broadcast(args("z")));
		settle(() -> Files.isRegularFile(root().resolve("survey")) && !read(root().resolve("survey")).isEmpty());
		assertThat(read(root().resolve("survey"))).as("heard however far (the sender's strength), nearest first")
			.isEqualTo(near.address() + " 3.0\n" + far.address() + " 30.0\n");
	}

	// The internet card.

	/** A web server on this machine, for the card (allowed to reach it), and the card's settings put back after. */
	private interface Web {
		void run(String url) throws Exception;
	}

	@Test
	void aProgramBlockedReadingTheCardsLogLeavesItsNewsForAnotherThatPolls() throws Exception {
		withWeb(url -> {
			type("cat /dev/internet0 > /tmp/log &\r"); // (blocked, read after read: it must not take curl's news)
			untilIdle();
			type("curl -s " + url + "/abc > /a; touch /done\r");
			untilFile(root().resolve("done"), "");
			assertThat(read(root().resolve("a"))).isEqualTo("abc".repeat(3000));
		});
	}

	@Test
	void readsSmallerThanAPieceGiveTheBodyWholeAndInOrderEvenWithItsQueueFull() throws Exception {
		withWeb(url -> {
			file("r.lua", """
				local sys = require("sys")
				local card = assert(sys.open("/dev/internet0"))
				local handle = assert(sys.ioctl(card, "request", "%s/abcdefghijklmnopqrstuvwxyz0123"))
				sys.sleep(1) -- (its queue fills: every piece it holds waits)
				local out = io.open("/got", "w")
				while true do
				  local piece = sys.ioctl(card, "read", handle, 700) -- (a piece is 4096: each split)
				  if piece == "" then break end
				  if piece then out:write(piece) else sys.poll({[card] = {PRI = true}}) end
				end
				out:close()
				""".formatted(url));
			type("lua /r.lua 2> /err; touch /done\r");
			untilFile(root().resolve("done"), "");
			assertThat(read(root().resolve("err"))).isEmpty();
			assertThat(read(root().resolve("got"))).as("96 KB: more pieces than it holds").isEqualTo("abcdefghijklmnopqrstuvwxyz0123".repeat(3000));
		});
	}

	// (the web server)
	private void withWeb(Web test) throws Exception {
		HttpServer web = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		web.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			byte[] body = switch (path) {
				case "/moved" -> {
					exchange.getResponseHeaders().add("Location", "/paste");
					exchange.sendResponseHeaders(307, -1);
					yield null;
				}
				case "/paste" -> (exchange.getRequestMethod() + " " + exchange.getRequestHeaders().getFirst("Content-Type") + " "
					+ new String(exchange.getRequestBody().readAllBytes())).getBytes();
				case "/missing" -> {
					exchange.sendResponseHeaders(404, -1);
					yield null;
				}
				default -> path.substring(1).repeat(3000).getBytes(); // (a file's text: its name, many times)
			};
			if (body != null) {
				exchange.getResponseHeaders().add("Content-Type", "text/plain");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			}
			exchange.close();
		});
		web.start();
		InternetCard.policyFrom(() -> new InternetPolicy(true, List.of("127.0.0.1"), List.of()));
		try {
			InternetCard card = new InternetCard();
			devices.put(Motherboard.exp(0), card);
			ticks.add(card::tick); // (its block's tick)
			boot();
			test.run("http://127.0.0.1:" + web.getAddress().getPort());
		} finally {
			InternetCard.policyFrom(() -> InternetPolicy.DEFAULT);
			web.stop(0);
		}
	}

	@Test
	void theFetchProgramDownloadsAUrlIntoAFile() throws Exception {
		withWeb(url -> {
			file("fetch.lua", example("internet-fetch"));
			type("lua /fetch.lua " + url + "/a.txt /a > /dev/null; echo $? > /st1; lua /fetch.lua " + url
				+ "/missing /m 2> /dev/null; echo $? > /st2\r");
			untilFile(root().resolve("st2"), "1\n");
			assertThat(read(root().resolve("st1"))).isEqualTo("0\n");
			assertThat(read(root().resolve("a"))).as("every byte").isEqualTo("a.txt".repeat(3000));
			assertThat(root().resolve("m")).as("a 404 writes nothing").doesNotExist();
		});
	}

	@Test
	void theMirrorProgramDownloadsSeveralFilesAtOnce() throws Exception {
		withWeb(url -> {
			file("mirror.lua", example("internet-mirror"));
			type("mkdir /m; lua /mirror.lua " + url + "/ /m one two three four five six 2> /err; echo $? > /st\r");
			untilFile(root().resolve("st"), "0\n");
			assertThat(read(root().resolve("err"))).isEmpty();
			for (String name : List.of("one", "two", "three", "four", "five", "six")) {
				assertThat(read(root().resolve("m/" + name))).as(name).isEqualTo(name.repeat(3000));
			}
		});
	}

	@Test
	void thePostProgramSendsAFileFollowsARedirectAndWritesTheAnswer() throws Exception {
		withWeb(url -> {
			file("post.lua", example("internet-post"));
			file("note.txt", "remember the milk");
			type("lua /post.lua " + url + "/moved /note.txt text/markdown > /answer 2> /err; echo $? > /st\r");
			untilFile(root().resolve("st"), "0\n");
			assertThat(read(root().resolve("answer"))).as("a 307 keeps the POST and its body").isEqualTo("POST text/markdown remember the milk");
			assertThat(read(root().resolve("err"))).as("where the answer came from").startsWith(url + "/paste: 200");
		});
	}

	@Test
	void theLogProgramKeepsEveryRequestTheCardMakes() throws Exception {
		withWeb(url -> {
			file("log.lua", example("internet-log"));
			type("lua /log.lua /log &\r");
			untilIdle();
			type("curl -s " + url + "/b > /dev/null; curl -s " + url + "/missing > /dev/null; touch /done\r");
			untilFile(root().resolve("done"), "");
			settle(() -> Files.isRegularFile(root().resolve("log")) && read(root().resolve("log")).lines().count() == 2);
			List<String> lines = read(root().resolve("log")).lines().toList();
			assertThat(lines.get(0)).matches("GET " + java.util.regex.Pattern.quote(url) + "/b 200 \\d+ms 3000");
			assertThat(lines.get(1)).matches("GET " + java.util.regex.Pattern.quote(url) + "/missing 404 \\d+ms 0");
		});
	}
}
