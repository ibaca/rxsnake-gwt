package rxsnake.client;

import static java.lang.Math.floor;
import static java.lang.Math.max;
import static java.lang.Math.random;
import static java.util.Arrays.copyOfRange;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static rx.Observable.combineLatest;
import static rx.Observable.defer;
import static rx.Observable.just;
import static rx.Observable.merge;
import static rxsnake.client.RxGwt.click;
import static rxsnake.client.RxGwt.keyUp;
import static rxsnake.client.RxGwt.touchStart;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.dom.client.HasTouchStartHandlers;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import rx.Observable;
import rx.Observable.Transformer;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;

/**
 * Original idea from http://philipnilsson.github.io/badness
 * Style from https://github.com/staltz/flux-challenge
 */
public class RxSnake implements EntryPoint {
    static final Logger log = Logger.getLogger(RxSnake.class.getName());
    static final Document doc = Document.get();
    static final Anchor cLeft = Anchor.wrap(doc.getElementById("c-left"));
    static final Anchor cRight = Anchor.wrap(doc.getElementById("c-right"));
    static final Label msg = Label.wrap(doc.getElementById("log"));
    static final RootPanel root = RootPanel.get();
    static final XY size = new XY(20, 20);

    @Override public void onModuleLoad() {
        Draw.game(size);

        Input inputs = new Input();
        Func0<Observable<XY>> position = () -> position(inputs);
        Func0<Observable<Game>> newGame = () -> game(position);

        repeated(newGame, inputs.restart).subscribe(e -> {
            Draw.snakeTail.call(e.snake);
            Draw.snakeHead.call(copyOfRange(e.snake, max(0, e.snake.length - 1), e.snake.length));
            Draw.apple.call(new XY[] { e.apple });
            Draw.score(e.score);
        });
    }

    static Observable<Game> repeated(Func0<Observable<Game>> game, Observable<?> restart) {
        return Observable.concat(
                game.call().doOnSubscribe(Draw::logClear).doOnUnsubscribe(Draw::logRestart),
                defer(() -> restart.take(1).flatMap(n -> repeated(game, restart))));
    }

    static Observable<XY> position(Input input) {
        XY startDirection = new XY(0, 1), startPosition = new XY(0, 0);

        @SuppressWarnings("SuspiciousNameCombination")
        Observable<Func1<XY, XY>> actions = merge(
                just(n -> n) /* initial action */,
                input.left.map(n -> p -> new XY(p.y, -p.x)),
                input.right.map(n -> p -> new XY(-p.y, p.x)));

        Observable<XY> direction = actions.scan(startDirection, (x, f) -> f.call(x)).compose(info("direction"));
        Observable<XY> directionTicks = input.tick.withLatestFrom(direction, (t, d) -> d);
        Observable<XY> position = directionTicks.scan(startPosition, XY::add).compose(info("position"));

        return position.share();
    }

    static Observable<Game> game(Func0<Observable<XY>> position) {
        Observable<XY> pos = position.call();
        Observable<XY> apple = apple(pos).cache(1).compose(info("apple"));

        class PosLength {
            XY pos;
            int length;
            PosLength(XY pos, int length) { this.pos = pos; this.length = length; }
        }
        Observable<Integer> length = apple.map(n -> 1).scan(10 - 1, (x, y) -> x + y).compose(info("length"));
        Observable<Integer> score = apple.map(n -> 1).scan(0 - 1, (x, y) -> x + y).compose(info("score"));
        //noinspection Convert2MethodRef this method reference doesn't work --v
        Observable<XY[]> snake = pos.withLatestFrom(length, (p, l) -> new PosLength(p, l))
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
        Observable<KeyUpEvent> keyUp = keyUp(root);
        Observable<Integer> keyCode = keyUp.map(e -> e.getNativeEvent().getKeyCode()).compose(info("key"));
        Observable<?> left = merge(keyCode.filter(x -> x == 37), tap(cLeft));
        Observable<?> right = merge(keyCode.filter(x -> x == 39), tap(cRight));
        Observable<?> restart = merge(keyCode.filter(x -> x == 82), tap(msg));
        Observable<?> tick = Observable.interval(200, MILLISECONDS).map(Long::intValue).compose(info("tick"));
    }

    static <T extends HasClickHandlers & HasTouchStartHandlers> Observable<?> tap(T a) {
        return merge(click(a), touchStart(a)).throttleFirst(300, MILLISECONDS);
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
            Element game = doc.getElementById("game");
            String html = "";
            for (int i = 0; i < size.x; i++) {
                html += "<div class=row>";
                for (int j = 0; j < size.y; j++) html += "<span class=cell></span>";
                html += "</div>";
            }
            game.setInnerHTML(html);
        }

        static Action1<XY[]> fillCells(String className) {
            Element game = doc.getElementById("game");
            return (ps) -> {
                NodeList<Element> cells = querySelectorAll(game, "." + className);
                for (int i = 0; i < cells.getLength(); i++) cells.getItem(i).removeClassName(className);
                for (XY p : ps) game.getChild(p.y).getChild(p.x).<Element>cast().addClassName(className);
            };
        }
        static Action1<XY[]> apple = fillCells("apple");
        static Action1<XY[]> snakeHead = fillCells("snake-head");
        static Action1<XY[]> snakeTail = fillCells("snake-tail");

        static void logRestart() { doc.getElementById("log").setInnerHTML("Press 'r' to restart"); }

        static void logClear() { doc.getElementById("log").setInnerHTML("Press left/right to steer"); }

        static void score(int score) { doc.getElementById("score").setInnerHTML("Score: " + score); }
    }

    private static <T> Transformer<T, T> info(String action) {
        if (!log.isLoggable(Level.INFO)) return o -> o;
        else return o -> o.doOnNext(n -> log.info(action + ": " + Objects.toString(n)));
    }

    static native Element querySelector(Element root, String selector) /*-{
        return root.querySelector(selector);
    }-*/;

    static native NodeList<Element> querySelectorAll(Element root, String selector) /*-{
        return root.querySelectorAll(selector);
    }-*/;
}
