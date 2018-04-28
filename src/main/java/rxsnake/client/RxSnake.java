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

import com.google.gwt.core.client.EntryPoint;
import elemental2.dom.Console;
import elemental2.dom.Element;
import elemental2.dom.EventTarget;
import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.functions.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import jsinterop.base.Js;

/**
 * Original idea from http://philipnilsson.github.io/badness
 * Style from https://github.com/staltz/flux-challenge
 */
public class RxSnake implements EntryPoint {
    static final Console L = console;

    static class XY {
        final int x, y;
        XY(int x, int y) {
            this.x = x;
            this.y = y;
        }
        public XY add(XY in, XY bound) {
            return new XY((x + in.x + bound.x) % bound.x, (y + in.y + bound.y) % bound.y);
        }
        @SuppressWarnings("SuspiciousNameCombination") XY rotateL() { return new XY(y, -x); }
        @SuppressWarnings("SuspiciousNameCombination") XY rotateR() { return new XY(-y, x); }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof XY)) return false;
            XY XY = (XY) o; return x == XY.x && y == XY.y;
        }
        @Override public int hashCode() { return Objects.hash(x, y); }
        @Override public String toString() { return "{x=" + x + ", y=" + y + '}'; }
    }
    @Override public void onModuleLoad() {
        XY size = new XY(20, 20);

        Supplier<XY> randomPos = () -> new XY((int) floor(random() * size.x), (int) floor(random() * size.y));

        // References
        var cLeft = document.getElementById("c-left");
        var cRight = document.getElementById("c-right");
        var msg = document.getElementById("log");
        var game = document.getElementById("game");
        var out = document.getElementById("log");

        // Inputs
        var keyUp = fromEvent(document, keyup).compose(info("key"));
        var left = merge(keyUp.filter(ArrowLeft::match), tap(cLeft));
        var right = merge(keyUp.filter(ArrowRight::match), tap(cRight));
        var restart = merge(keyUp.filter(x -> Objects.equals(x.key, "r")), tap(msg));
        var pause = merge(keyUp.filter(Spacebar::match), tap(msg));

        Observable<XY> position$ = Observable.defer(() -> {
            XY startDirection = new XY(0, 1), startPosition = new XY(0, 0);
            var actions = Observable.<UnaryOperator<XY>>merge(left.map(n -> XY::rotateL), right.map(n -> XY::rotateR));
            var direction = actions.scan(startDirection, (x, f) -> f.apply(x)).compose(info("direction"));
            return Observable.interval(200, MILLISECONDS).map(Long::intValue).takeUntil(pause)
                    .concatWith(pause.take(1).ignoreElements().toObservable()).repeat().compose(info("tick"))
                    .withLatestFrom(direction, (t, d) -> d).scan(startPosition, (xy, in) -> xy.add(in, size));
        }).compose(info("position")).share();

        class Game {
            final XY[] snake;
            final XY apple;
            final Integer score;
            Game(XY[] snake, XY apple, Integer score) { this.snake = snake; this.apple = apple; this.score = score; }
            @Override public String toString() {
                return "Game{snake.length=" + snake.length + ", apple=" + apple + ", score=" + score + '}';
            }
        }

        class PosLength {
            public XY pos;
            public int length;
            PosLength(XY pos, int length) { this.pos = pos; this.length = length; }
        }

        Observable<XY> apple$ = Observable.defer(() -> {
            XY appleXY = randomPos.get(); Predicate<XY> match = xy -> xy.equals(appleXY);
            return position$.takeUntil(match).ignoreElements().startWith(just(appleXY));
        }).repeat().compose(info("apple"));

        Observable<Game> game$ = Observable.defer(() -> apple$.publish(apple -> {
            Observable<Integer> length = apple.scan(10 - 1, (x, y) -> x + 1);
            Observable<Integer> score = apple.scan(0 - 1, (x, y) -> x + 1);
            Observable<XY[]> snake = position$
                    .withLatestFrom(length, PosLength::new)
                    .scan(new ArrayList<>(), (List<XY> acc, PosLength n) -> {
                        acc.add(n.pos);
                        if (acc.size() > n.length) acc.remove(0);
                        return acc;
                    }).map(n -> n.toArray(new XY[0])).share();
            Observable<XY[]> dead = snake.filter(n -> {
                for (int i = 0; i < n.length - 1; i++) if (n[i].equals(n[n.length - 1])) return true;
                return false;
            });
            return combineLatest(snake, apple, score, Game::new).takeUntil(dead);
        })).compose(info("game"));

        // draw the game board
        String row = range(0, size.y).mapToObj(y -> "<span class=cell></span>").collect(joining());
        game.innerHTML = range(0, size.x).mapToObj(x -> "<div class=row>" + row + "</div>").collect(joining());

        BiConsumer<String, XY[]> fillCells = (className, xy) -> {
            iterator(game.querySelectorAll("." + className)).forEachRemaining(el -> el.classList.remove(className));
            for (XY p : xy) Js.<Element>cast(game.childNodes.item(p.y).childNodes.item(p.x)).classList.add(className);
        };

        // bind drawings and game restart
        game$.doOnSubscribe(s -> out.innerHTML = "Press left/right to steer")
                .doOnDispose(() -> out.innerHTML = "Press 'r' to restart")
                .doOnNext(e -> {
                    fillCells.accept("snake-tail", e.snake);
                    fillCells.accept("snake-head", copyOfRange(e.snake, max(0, e.snake.length - 1), e.snake.length));
                    fillCells.accept("apple", new XY[] { e.apple });
                    document.getElementById("score").innerHTML = "Score: " + e.score;
                })
                .ignoreElements().andThen(restart.take(1)).repeat().subscribe();
    }


    static Observable<?> tap(EventTarget src) {
        return merge(fromEvent(src, click), fromEvent(src, touchstart)).throttleFirst(300, MILLISECONDS);
        // fixes duplicate events received in mobiles --^
    }

    static <T> ObservableTransformer<T, T> info(String action) {
        return o -> o.doOnNext(n -> L.info(action + ": " + n));
    }
}
