package space.potatofrom.aeiodine;

/**
 * Created by kevin on 7/25/16.
 */
public class IodineArgument {
    private final IodineFlags flag;
    private final String value;

    public IodineArgument(IodineFlags flag) {
        this(flag, null);
    }

    public IodineArgument(IodineFlags flag, String value) {
        this.flag = flag;
        this.value = value;
    }

    public IodineFlags getFlag() {
        return flag;
    }
    public String getValue() {
        return value;
    }
}
