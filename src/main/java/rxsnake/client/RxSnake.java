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
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.range;
import static org.jboss.gwt.elemento.core.Elements.iterator;
import static org.jboss.gwt.elemento.core.EventType.click;
import static org.jboss.gwt.elemento.core.EventType.keyup;
import static org.jboss.gwt.elemento.core.EventType.touchstart;
import static org.jboss.gwt.elemento.core.Key.ArrowLeft;
import static org.jboss.gwt.elemento.core.Key.ArrowRight;
import static org.jboss.gwt.elemento.core.Key.Spacebar;
import static rxsnake.client.RxSnake.size;

import com.google.gwt.core.client.EntryPoint;
import elemental2.dom.Console;
import elemental2.dom.Element;
import elemental2.dom.EventTarget;
import elemental2.dom.HTMLAnchorElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.KeyboardEvent;
import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.internal.operators.observable.ObservableBuffer;
import java.util.ArrayList;
import java.util.Objects;
import jsinterop.base.Js;

/**
 * Original idea from http://philipnilsson.github.io/badness
 * Style from https://github.com/staltz/flux-challenge
 */
public class RxSnake implements EntryPoint {
    static final Console L = console;

    // References
    static final XY size = new XY(20, 20);
    static final HTMLAnchorElement cLeft = Js.cast(document.getElementById("c-left"));
    static final HTMLAnchorElement cRight = Js.cast(document.getElementById("c-right"));
    static final HTMLElement msg = Js.cast(document.getElementById("log"));
    @SuppressWarnings("SuspiciousNameCombination") static final Function<XY, XY> rotateL = p -> new XY(p.y, -p.x);
    @SuppressWarnings("SuspiciousNameCombination") static final Function<XY, XY> rotateR = p -> new XY(-p.y, p.x);

    // Inputs
    static final Observable<KeyboardEvent> keyUp = fromEvent(document, keyup).compose(info("key"));
    static final Observable<?> left = merge(keyUp.filter(ArrowLeft::match), tap(cLeft));
    static final Observable<?> right = merge(keyUp.filter(ArrowRight::match), tap(cRight));
    static final Observable<?> restart = merge(keyUp.filter(x -> Objects.equals(x.key, "r")), tap(msg));
    static final Observable<?> pause = merge(keyUp.filter(Spacebar::match), tap(msg));

    @Override public void onModuleLoad() {
        Draw.game(size); // draw the game board

        Observable<XY> position$ = Observable.defer(() -> {
            XY startDirection = new XY(0, 1), startPosition = new XY(0, 0);
            Observable<Function<XY, XY>> actions = merge(just(n -> n), left.map(n -> rotateL), right.map(n -> rotateR));
            Observable<XY> direction = actions.scan(startDirection, (x, f) -> f.apply(x)).compose(info("direction"));
            return Observable.interval(200, MILLISECONDS).map(Long::intValue).takeUntil(pause)
                    .concatWith(pause.take(1).ignoreElements().toObservable()).repeat().compose(info("tick"))
                    .withLatestFrom(direction, (t, d) -> d).scan(startPosition, XY::add);
        }).compose(info("position")).share();

        Observable<XY> apple$ = ObservableBuffer.defer(() -> {
            XY appleXY = randomPos(); Predicate<XY> match = xy -> xy.equals(appleXY);
            return position$.takeUntil(match).ignoreElements().startWith(just(appleXY));
        }).repeat().compose(info("apple")).replay(1).refCount();

        Observable<Game> game$ = Observable.defer(() -> {
            Observable<Integer> length = apple$.scan(10 - 1, (x, y) -> x + 1);
            Observable<Integer> score = apple$.scan(0 - 1, (x, y) -> x + 1);
            Observable<XY[]> snake = position$.withLatestFrom(length, PosLength::new)
                    .scan(new ArrayList<XY>(), (acc, n) -> {
                        acc.add(n.pos); if (acc.size() > n.length) acc.remove(0); return acc;
                    })
                    .map(n -> n.toArray(new XY[n.size()])).share();
            Observable<XY[]> dead = snake.filter(n -> {
                for (int i = 0; i < n.length - 1; i++) if (n[i].equals(n[n.length - 1])) return true;
                return false;
            });
            return combineLatest(snake, apple$, score, Game::new).takeUntil(dead);
        }).compose(info("game"));

        game$.doOnNext(Draw::draw) // bind drawings and game restart
                .doOnLifecycle(s -> Draw.logClear(), Draw::logRestart)
                .ignoreElements().andThen(restart.take(1)).repeat().subscribe();
    }

    static XY randomPos() { return new XY((int) floor(random() * size.y), (int) floor(random() * size.x)); }

    static Observable<?> tap(EventTarget src) {
        return merge(fromEvent(src, click), fromEvent(src, touchstart)).throttleFirst(300, MILLISECONDS);
        // fixes duplicate events received in mobiles --^
    }

    static class Draw {
        static final Element game = document.getElementById("game");
        static final Element out = document.getElementById("log");

        static void game(XY size) {
            String row = range(0, size.y).mapToObj(y -> "<span class=cell></span>").collect(joining());
            game.innerHTML = range(0, size.x).mapToObj(x -> "<div class=row>" + row + "</div>").collect(joining());
        }

        static void draw(Game e) throws Exception {
            fillCells("snake-tail", e.snake);
            fillCells("snake-head", copyOfRange(e.snake, max(0, e.snake.length - 1), e.snake.length));
            fillCells("apple", new XY[] { e.apple });
            score(e.score);
        }

        static void fillCells(String className, XY[] xy) {
            iterator(game.querySelectorAll("." + className)).forEachRemaining(el -> el.classList.remove(className));
            for (XY p : xy) Js.<Element>cast(game.childNodes.item(p.y).childNodes.item(p.x)).classList.add(className);
        }

        static void logRestart() { out.innerHTML = "Press 'r' to restart"; }
        static void logClear() { out.innerHTML = "Press left/right to steer"; }
        static void score(int score) { document.getElementById("score").innerHTML = "Score: " + score; }
    }

    private static <T> ObservableTransformer<T, T> info(String action) {
        return o -> o.doOnNext(n -> L.info(action + ": " + n));
    }
}

class XY {
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

class PosLength {
    XY pos;
    int length;
    PosLength(XY pos, int length) { this.pos = pos; this.length = length; }
}

class Game {
    final XY[] snake;
    final XY apple;
    final Integer score;
    Game(XY[] snake, XY apple, Integer score) { this.snake = snake; this.apple = apple; this.score = score; }
    @Override public String toString() {
        return "Game{snake.length=" + snake.length + ", apple=" + apple + ", score=" + score + '}';
    }
}
