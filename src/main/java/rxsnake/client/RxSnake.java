package rxsnake.client;

import static com.intendia.rxgwt2.elemento.RxElemento.fromEvent;
import static elemental2.dom.DomGlobal.console;
import static elemental2.dom.DomGlobal.document;
import static io.reactivex.Observable.combineLatest;
import static io.reactivex.Observable.just;
import static io.reactivex.Observable.merge;
import static java.lang.Math.floor;
import static java.lang.Math.max;
import static java.lang.Math.random;
import static java.util.Arrays.copyOfRange;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.jboss.gwt.elemento.core.EventType.click;
import static org.jboss.gwt.elemento.core.EventType.keyup;
import static org.jboss.gwt.elemento.core.EventType.touchstart;
import static org.jboss.gwt.elemento.core.Key.ArrowLeft;
import static org.jboss.gwt.elemento.core.Key.ArrowRight;

import com.google.gwt.core.client.EntryPoint;
import elemental2.dom.Console;
import elemental2.dom.EventTarget;
import elemental2.dom.HTMLAnchorElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.KeyboardEvent;
import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import java.util.ArrayList;
import java.util.Objects;
import jsinterop.base.Js;

/**
 * Original idea from http://philipnilsson.github.io/badness
 * Style from https://github.com/staltz/flux-challenge
 */
public class RxSnake implements EntryPoint {
    static final Console L = console;
    static final HTMLAnchorElement cLeft = Js.cast(document.getElementById("c-left"));
    static final HTMLAnchorElement cRight = Js.cast(document.getElementById("c-right"));
    static final HTMLElement msg = Js.cast(document.getElementById("log"));
    static final XY size = new XY(20, 20);

    @Override public void onModuleLoad() {
        Draw.game(size);
        Input inputs = new Input();
        Observable<Game> game = game(position(inputs))
                .doOnSubscribe(s -> Draw.logClear())
                .doOnNext(this::draw)
                .doOnDispose(Draw::logRestart);
        game.ignoreElements()
                .andThen(inputs.restart.take(1))
                .repeat().subscribe();
    }

    private void draw(Game e) throws Exception {
        Draw.snakeTail.accept(e.snake);
        Draw.snakeHead.accept(copyOfRange(e.snake, max(0, e.snake.length - 1), e.snake.length));
        Draw.apple.accept(new XY[] { e.apple });
        Draw.score(e.score);
    }

    static Observable<XY> position(Input input) {
        return Observable.defer(() -> {
            XY startDirection = new XY(0, 1), startPosition = new XY(0, 0);

            @SuppressWarnings("SuspiciousNameCombination")
            Observable<Function<XY, XY>> actions = merge(
                    just(n -> n) /* initial action */,
                    input.left.map(n -> p -> new XY(p.y, -p.x)),
                    input.right.map(n -> p -> new XY(-p.y, p.x)));

            Observable<XY> direction = actions.scan(startDirection, (x, f) -> f.apply(x)).compose(info("direction"));
            Observable<XY> directionTicks = input.tick.withLatestFrom(direction, (t, d) -> d);
            Observable<XY> position = directionTicks.scan(startPosition, XY::add).compose(info("position"));

            return position.share();
        });
    }

    static class PosLength {
        XY pos;
        int length;
        PosLength(XY pos, int length) { this.pos = pos; this.length = length; }
    }

    static Observable<Game> game(Observable<XY> position) {
        return Observable.defer(() -> {
            Observable<XY> apple = apple(position).replay(1).refCount().compose(info("apple"));
            Observable<Integer> length = apple.map(n -> 1).scan(10 - 1, (x, y) -> x + y).compose(info("length"));
            Observable<Integer> score = apple.map(n -> 1).scan(0 - 1, (x, y) -> x + y).compose(info("score"));
            Observable<XY[]> snake = position.withLatestFrom(length, PosLength::new)
                    .scan(new ArrayList<XY>(), (acc, n) -> {
                        acc.add(n.pos); if (acc.size() > n.length) acc.remove(0); return acc;
                    })
                    .map(n -> n.toArray(new XY[n.size()]))
                    .compose(info("snake"))
                    .share();

            Observable<XY[]> dead = snake.filter(n -> {
                for (int i = 0; i < n.length - 1; i++) if (n[i].equals(n[n.length - 1])) return true;
                return false;
            });

            return combineLatest(snake, apple, score, Game::new).takeUntil(dead).compose(info("game")).share();
        });
    }

    static Observable<XY> apple(Observable<XY> position) {
        XY appleXY = randomPos();
        Observable<XY> match = position.filter(n -> Objects.equals(n, appleXY)).take(1);
        return match.switchMap(n -> apple(position)).startWith(appleXY);
    }

    static XY randomPos() { return new XY((int) floor(random() * size.y), (int) floor(random() * size.x)); }

    static class XY {
        final int x, y;
        XY(int x, int y) {
            this.x = x;
            this.y = y;
        }
        public XY add(XY in) { return new XY((x + in.x + size.x) % size.x, (y + in.y + size.y) % size.y); }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof XY)) return false;
            XY XY = (XY) o; return x == XY.x && y == XY.y;
        }
        @Override public int hashCode() { return Objects.hash(x, y); }
        @Override public String toString() { return "{x=" + x + ", y=" + y + '}'; }
    }

    static class Input {
        Observable<KeyboardEvent> keyUp = fromEvent(document, keyup).compose(info("key"));
        Observable<?> left = merge(keyUp.filter(ArrowLeft::match), tap(cLeft));
        Observable<?> right = merge(keyUp.filter(ArrowRight::match), tap(cRight));
        Observable<?> restart = merge(keyUp.filter(x -> Objects.equals(x.key, "r")), tap(msg));
        Observable<?> tick = Observable.interval(200, MILLISECONDS).map(Long::intValue).compose(info("tick"));
    }

    static Observable<?> tap(EventTarget src) {
        return merge(fromEvent(src, click), fromEvent(src, touchstart)).throttleFirst(300, MILLISECONDS);
        // fixes duplicate events received in mobiles --^
    }

    static class Game {
        final XY[] snake;
        final XY apple;
        final Integer score;
        Game(XY[] snake, XY apple, Integer score) { this.snake = snake; this.apple = apple; this.score = score; }
        @Override public String toString() {
            return "Game{snake.length=" + snake.length + ", apple=" + apple + ", score=" + score + '}';
        }
    }

    static class Draw {
        static void game(XY size) {
            elemental2.dom.Element game = document.getElementById("game");
            String html = "";
            for (int i = 0; i < size.x; i++) {
                html += "<div class=row>";
                for (int j = 0; j < size.y; j++) html += "<span class=cell></span>";
                html += "</div>";
            }
            game.innerHTML = html;
        }

        static Consumer<XY[]> fillCells(String className) {
            elemental2.dom.Element game = document.getElementById("game");
            return (ps) -> {
                elemental2.dom.NodeList<elemental2.dom.Element> cells = game.querySelectorAll("." + className);
                for (int i = 0; i < cells.getLength(); i++) {
                    Js.<HTMLElement>cast(cells.item(i)).classList.remove(className);
                }
                for (XY p : ps) {
                    Js.<HTMLElement>cast(game.childNodes.item(p.y).childNodes.item(p.x)).classList.add(className);
                }
            };
        }
        static Consumer<XY[]> apple = fillCells("apple");
        static Consumer<XY[]> snakeHead = fillCells("snake-head");
        static Consumer<XY[]> snakeTail = fillCells("snake-tail");

        static void logRestart() { document.getElementById("log").innerHTML = "Press 'r' to restart"; }

        static void logClear() { document.getElementById("log").innerHTML = "Press left/right to steer"; }

        static void score(int score) { document.getElementById("score").innerHTML = "Score: " + score; }
    }

    private static <T> ObservableTransformer<T, T> info(String action) {
        return o -> o.doOnNext(n -> L.info(action + ": " + n));
    }
}
