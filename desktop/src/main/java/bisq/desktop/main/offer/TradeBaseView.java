/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.offer;

import javax.inject.Inject;

import bisq.core.locale.Res;
import bisq.core.trade.Trade;
import bisq.core.user.Preferences;
import bisq.desktop.Navigation;
import bisq.desktop.common.model.Activatable;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.main.MainView;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class TradeBaseView extends ActivatableViewAndModel<TabPane, Activatable> {
	
	@FXML
    protected Tab tradeBuyTab, tradeSellTab;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private Tab selectedTab;
    private final Preferences preferences;
	private Trade.TradeBaseCurrency selectedBaseCurrency = Trade.TradeBaseCurrency.BTC;
	private Class<? extends OfferView> buyOfferViewClass = BuyOfferView.class;
	private Class<? extends OfferView> sellOfferViewClass = SellOfferView.class;

	@Inject
    public TradeBaseView(CachingViewLoader viewLoader, Navigation navigation, Preferences preferences) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.preferences = preferences;
    }

    @Override
    public void initialize() {
    	root.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tradeBuyTab.setText(Res.get("mainView.menu.trade.buy", selectedBaseCurrency.toString()).toUpperCase());
        tradeSellTab.setText(Res.get("mainView.menu.trade.sell", selectedBaseCurrency.toString()).toUpperCase());

        if(selectedTab == null) {
        	selectedTab = tradeBuyTab;
        }
        selectView();
        navigationListener = viewPath -> {
            if (viewPath.size() == 2 && navigation.getCurrentPath().get(1) == getClass()) {
            	selectView();
            }
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
        	selectedTab = newValue;
            if (newValue == tradeBuyTab) {
                loadView(buyOfferViewClass);
            } else if (newValue == tradeSellTab) {
                loadView(sellOfferViewClass);
            } else {
                loadView(buyOfferViewClass);
            }
        };       
    }

    private void selectView() {
        if (selectedTab == tradeBuyTab) {
            loadView(buyOfferViewClass);
        } else if (selectedTab == tradeSellTab) {
            loadView(sellOfferViewClass);
        } else {
            loadView(buyOfferViewClass);
        }
	}

	@Override
    protected void activate() {
    	navigation.addListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);

        if (navigation.getCurrentPath().size() == 3 && navigation.getCurrentPath().get(1) == getClass()) {
            Tab selectedItem = root.getSelectionModel().getSelectedItem();
            if (selectedItem == tradeBuyTab) {
            	//TODO(niyid) Replace navigateTo with navigateToWithData passing selectedBaseCurrency and direction (OfferPayload.Direction instance).
             	navigation.navigateTo(MainView.class, getClass(), buyOfferViewClass);
            }
            else if (selectedItem == tradeSellTab) {
            	//TODO(niyid) Replace navigateTo with navigateToWithData passing selectedBaseCurrency and direction (OfferPayload.Direction instance).
               	navigation.navigateTo(MainView.class, getClass(), sellOfferViewClass);
            }
            loadView(navigation.getCurrentPath().get(2));
        }
    }

    @Override
    protected void deactivate() {
        navigation.removeListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
    }

    private void loadView(Class<? extends View> viewClass) {
        if (selectedTab != null && selectedTab.getContent() != null) {
            if (selectedTab.getContent() instanceof ScrollPane) {
                ((ScrollPane) selectedTab.getContent()).setContent(null);
            } else {
                selectedTab.setContent(null);
            }
        }

        View view = viewLoader.load(viewClass);
        if (viewClass == buyOfferViewClass) {
            selectedTab = tradeBuyTab;
        } else if (viewClass == sellOfferViewClass) {
            selectedTab = tradeSellTab;
        }

        selectedTab.setContent(view.getRoot());
        root.getSelectionModel().select(selectedTab);
    }
}

