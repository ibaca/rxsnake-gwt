package rxsnake.client;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DragEndEvent;
import com.google.gwt.event.dom.client.DragEnterEvent;
import com.google.gwt.event.dom.client.DragLeaveEvent;
import com.google.gwt.event.dom.client.DragOverEvent;
import com.google.gwt.event.dom.client.DragStartEvent;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.dom.client.HasDoubleClickHandlers;
import com.google.gwt.event.dom.client.HasDragEndHandlers;
import com.google.gwt.event.dom.client.HasDragEnterHandlers;
import com.google.gwt.event.dom.client.HasDragLeaveHandlers;
import com.google.gwt.event.dom.client.HasDragOverHandlers;
import com.google.gwt.event.dom.client.HasDragStartHandlers;
import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.HasKeyPressHandlers;
import com.google.gwt.event.dom.client.HasKeyUpHandlers;
import com.google.gwt.event.dom.client.HasMouseDownHandlers;
import com.google.gwt.event.dom.client.HasMouseMoveHandlers;
import com.google.gwt.event.dom.client.HasMouseOutHandlers;
import com.google.gwt.event.dom.client.HasMouseOverHandlers;
import com.google.gwt.event.dom.client.HasMouseUpHandlers;
import com.google.gwt.event.dom.client.HasMouseWheelHandlers;
import com.google.gwt.event.dom.client.HasTouchCancelHandlers;
import com.google.gwt.event.dom.client.HasTouchEndHandlers;
import com.google.gwt.event.dom.client.HasTouchMoveHandlers;
import com.google.gwt.event.dom.client.HasTouchStartHandlers;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseWheelEvent;
import com.google.gwt.event.dom.client.TouchCancelEvent;
import com.google.gwt.event.dom.client.TouchEndEvent;
import com.google.gwt.event.dom.client.TouchMoveEvent;
import com.google.gwt.event.dom.client.TouchStartEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Widget;
import rx.Observable;
import rx.Subscriber;
import rx.subscriptions.Subscriptions;

public class RxGwt {
    public static Observable<ClickEvent> click(HasClickHandlers source) {
        return Observable.create(s -> register(s, source.addClickHandler(s::onNext)));
    }

    public static Observable<DoubleClickEvent> doubleClick(HasDoubleClickHandlers source) {
        return Observable.create(s -> register(s, source.addDoubleClickHandler(s::onNext)));
    }

    public static Observable<DragEndEvent> dragEnd(HasDragEndHandlers source) {
        return Observable.create(s -> register(s, source.addDragEndHandler(s::onNext)));
    }

    public static Observable<DragEnterEvent> dragEnter(HasDragEnterHandlers source) {
        return Observable.create(s -> register(s, source.addDragEnterHandler(s::onNext)));
    }

    public static Observable<DragLeaveEvent> dragLeave(HasDragLeaveHandlers source) {
        return Observable.create(s -> register(s, source.addDragLeaveHandler(s::onNext)));
    }

    public static Observable<DragOverEvent> dragOver(HasDragOverHandlers source) {
        return Observable.create(s -> register(s, source.addDragOverHandler(s::onNext)));
    }

    public static Observable<DragStartEvent> dragStart(HasDragStartHandlers source) {
        return Observable.create(s -> register(s, source.addDragStartHandler(s::onNext)));
    }

    public static Observable<KeyDownEvent> keyDown(HasKeyDownHandlers source) {
        return Observable.create(s -> register(s, source.addKeyDownHandler(s::onNext)));
    }

    public static Observable<KeyUpEvent> keyUp(HasKeyUpHandlers source) {
        return Observable.create(s -> register(s, source.addKeyUpHandler(s::onNext)));
    }

    public static Observable<KeyUpEvent> keyUp(Widget source) {
        return Observable.create(s -> register(s, source.addDomHandler(s::onNext, KeyUpEvent.getType())));
    }

    public static Observable<KeyPressEvent> keyPress(HasKeyPressHandlers source) {
        return Observable.create(s -> register(s, source.addKeyPressHandler(s::onNext)));
    }

    public static Observable<KeyPressEvent> keyPress(HasKeyPressHandlers source, char filter) {
        return keyPress(source).filter(e -> filter == e.getCharCode());
    }

    public static Observable<MouseDownEvent> mouseDown(HasMouseDownHandlers source) {
        return Observable.create(s -> register(s, source.addMouseDownHandler(s::onNext)));
    }

    public static Observable<MouseMoveEvent> mouseMove(HasMouseMoveHandlers source) {
        return Observable.create(s -> register(s, source.addMouseMoveHandler(s::onNext)));
    }

    public static Observable<MouseOutEvent> mouseOut(HasMouseOutHandlers source) {
        return Observable.create(s -> register(s, source.addMouseOutHandler(s::onNext)));
    }

    public static Observable<MouseOverEvent> mouseOver(HasMouseOverHandlers source) {
        return Observable.create(s -> register(s, source.addMouseOverHandler(s::onNext)));
    }

    public static Observable<MouseUpEvent> mouseUp(HasMouseUpHandlers source) {
        return Observable.create(s -> register(s, source.addMouseUpHandler(s::onNext)));
    }

    public static Observable<MouseWheelEvent> mouseWheel(HasMouseWheelHandlers source) {
        return Observable.create(s -> register(s, source.addMouseWheelHandler(s::onNext)));
    }

    public static Observable<TouchCancelEvent> touchCancel(HasTouchCancelHandlers source) {
        return Observable.create(s -> register(s, source.addTouchCancelHandler(s::onNext)));
    }

    public static Observable<TouchEndEvent> touchEnd(HasTouchEndHandlers source) {
        return Observable.create(s -> register(s, source.addTouchEndHandler(s::onNext)));
    }

    public static Observable<TouchMoveEvent> touchMove(HasTouchMoveHandlers source) {
        return Observable.create(s -> register(s, source.addTouchMoveHandler(s::onNext)));
    }

    public static Observable<TouchStartEvent> touchStart(HasTouchStartHandlers source) {
        return Observable.create(s -> register(s, source.addTouchStartHandler(s::onNext)));
    }

    public static void register(Subscriber<?> s, HandlerRegistration handlerRegistration) {
        s.add(Subscriptions.create(handlerRegistration::removeHandler));
    }
}
