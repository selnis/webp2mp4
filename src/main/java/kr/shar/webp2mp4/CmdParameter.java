package kr.shar.webp2mp4;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to save command line parameters.
 */
public class CmdParameter {
    private String key;
    private List<String> value;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public List<String> getValue() {
        return value;
    }

    public void setValue(List<String> value) {
        this.value = value;
    }

    public void addValue(String value) {
        if (this.value == null) this.value = new ArrayList<String>();
        this.value.add(value);
    }

    @Override
    public String toString() {
        return getKey() + "=" + getValue();
    }
}